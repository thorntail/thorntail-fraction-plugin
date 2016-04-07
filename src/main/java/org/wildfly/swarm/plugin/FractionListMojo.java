/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(
        name = "fraction-list",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class FractionListMojo extends AbstractExposedComponentsMojo {

    private static final String FRACTION_TAGS_PROPERTY_NAME = "swarm.fraction.tags";
    private static final String FRACTION_INTERNAL_PROPERTY_NAME = "swarm.fraction.internal";

    @Inject
    ProjectBuilder projectBuilder;

    public void execute() throws MojoExecutionException, MojoFailureException {

        List<Dependency> dependencies = bomDependencies();

        List<Dependency> fractionsDependencies = bomDependencies().stream()
                .filter(this::isFraction)
                .collect(Collectors.toList());

        Map<String, Fraction> fractions = new TreeMap<>();
        fractionsDependencies.forEach(d -> fractions.put(d.getGroupId() + ":" + d.getArtifactId(),
                                                         new Fraction(d.getGroupId(), d.getArtifactId(), d.getVersion())));

        fractionsDependencies.forEach(dependency -> {
            final Fraction current = fractions.get(dependency.getGroupId() + ":" + dependency.getArtifactId());

            try {
                MavenProject fractionProject = project(dependency);

                current.setName(fractionProject.getName());
                current.setDescription(fractionProject.getDescription());
                Properties properties = fractionProject.getProperties();
                current.setTags(properties.getProperty(FRACTION_TAGS_PROPERTY_NAME, ""));
                current.setInternal(Boolean.parseBoolean(properties.getProperty(FRACTION_INTERNAL_PROPERTY_NAME)));

                Set<Artifact> deps = fractionProject.getArtifacts();

                for (Artifact each : deps) {
                    Fraction f = fractions.get(each.getGroupId() + ":" + each.getArtifactId());
                    if (f == null) {
                        continue;
                    }
                    current.addDependency(f);
                }

            } catch (ProjectBuildingException e) {
                e.printStackTrace();
            }
        });

        generateTxt(fractions);
        generateJSON(fractions);
        generateJavascript(fractions);
    }

    protected void generateTxt(Map<String, Fraction> fractions) {

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.txt");

        outFile.getParentFile().mkdirs();

        try (FileWriter out = new FileWriter(outFile)) {
            for (Fraction each : fractions.values()) {
                out.write(each.toString());
                out.write(" = ");
                out.write(each.getDependenciesString());
                out.write("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        org.apache.maven.artifact.DefaultArtifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "compile",
                "txt",
                "",
                new DefaultArtifactHandler("txt")
        );

        artifact.setFile(outFile);
        this.project.addAttachedArtifact(artifact);
    }

    protected void generateJSON(Map<String, Fraction> fractions) {
        ObjectMapper mapper = new ObjectMapper();

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.json");

        try {
            mapper.writeValue(outFile, fractions.values());
        } catch (IOException e) {
            e.printStackTrace();
        }

        org.apache.maven.artifact.DefaultArtifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "compile",
                "json",
                "",
                new DefaultArtifactHandler("json")
        );

        artifact.setFile(outFile);
        this.project.addAttachedArtifact(artifact);
    }

    protected void generateJavascript(Map<String, Fraction> fractions) {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.js");

        try (FileWriter writer = new FileWriter(outFile)) {

            writer.write("fractionList = ");
            writer.flush();

            mapper.writeValue(writer, fractions.values());

            writer.write(";");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        org.apache.maven.artifact.DefaultArtifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "compile",
                "js",
                "",
                new DefaultArtifactHandler("js")
        );

        artifact.setFile(outFile);
        this.project.addAttachedArtifact(artifact);
    }

    protected MavenProject project(Dependency dependency) throws ProjectBuildingException {
        ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        request.setProcessPlugins(false);
        request.setSystemProperties(System.getProperties());
        request.setRemoteRepositories(this.project.getRemoteArtifactRepositories());
        request.setRepositorySession(this.repositorySystemSession);
        request.setResolveDependencies(true);
        org.apache.maven.artifact.Artifact artifact =
                new org.apache.maven.artifact.DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                                                              dependency.getVersion(), "compile", "", "",
                                                              new DefaultArtifactHandler());
        MavenProject project = projectBuilder.build(artifact, request).getProject();

        return project;

    }

    protected boolean isFraction(Dependency dep) {
        return isFraction(dep, false);
    }

    protected boolean isFraction(Dependency dep, boolean tryApi) {
        if (!dep.getType().equals("jar")) {
            return false;
        }
        if (!dep.getGroupId().equals("org.wildfly.swarm")) {
            return false;
        }

        try {
            final File file = resolveArtifact(dep.getGroupId(), dep.getArtifactId() + (tryApi ? "-api" : ""),
                                              dep.getVersion(), "", "jar");
            if (file != null) {
                try (JarFile jar = new JarFile(file)) {
                    ZipEntry bootstrap = jar.getEntry("wildfly-swarm-bootstrap.conf");
                    if (bootstrap != null) {
                        return true;
                    }
                }
                if (!tryApi) {
                    return isFraction(dep, true);
                }
                return false;
            }
        } catch (ArtifactResolutionRuntimeException | IOException e) {
            // ignore
        }

        return false;
    }
}
