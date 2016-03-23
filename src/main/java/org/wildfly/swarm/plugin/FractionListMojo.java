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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

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
public class FractionListMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {

        List<Dependency> dependencies = this.project.getDependencyManagement().getDependencies();

        List<Dependency> fractionsDependencies = new ArrayList<>();

        for (Dependency dependency : dependencies) {
            if (isFraction(dependency)) {
                fractionsDependencies.add(dependency);
            }
        }

        Map<String, Fraction> fractions = new HashMap<>();

        for (Dependency dependency : fractionsDependencies) {
            fractions.put(dependency.getGroupId() + ":" + dependency.getArtifactId(), new Fraction(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion()));
        }

        for (Dependency dependency : fractionsDependencies) {
            Fraction current = fractions.get(dependency.getGroupId() + ":" + dependency.getArtifactId());
            try {
                MavenProject fractionProject = project(dependency);

                current.setDescription(fractionProject.getDescription());

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
        }

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
            mapper.writeValue(outFile, fractions);
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

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.js");

        try {
            FileWriter writer = new FileWriter(outFile);

            writer.write("fractionList = ");
            writer.flush();

            mapper.writeValue(writer, fractions);

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
        request.setRepositorySession(repositorySystemSession);
        request.setResolveDependencies(true);
        org.apache.maven.artifact.Artifact artifact = new org.apache.maven.artifact.DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), "compile", "", "", new DefaultArtifactHandler());
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
        ArtifactRequest req = new ArtifactRequest();
        org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(dep.getGroupId() + ":" + dep.getArtifactId() + (tryApi ? "-api" : "") + ":" + dep.getVersion());
        req.setArtifact(artifact);
        req.setRepositories(this.project.getRemoteProjectRepositories());

        try {
            ArtifactResult artifactResult = this.resolver.resolveArtifact(repositorySystemSession, req);
            if (artifactResult.isResolved()) {
                File file = artifactResult.getArtifact().getFile();
                JarFile jar = new JarFile(file);

                ZipEntry bootstrap = jar.getEntry("wildfly-swarm-bootstrap.conf");
                if (bootstrap != null) {
                    return true;
                }
                if (!tryApi) {
                    return isFraction(dep, true);
                }
                return false;
            }
        } catch (ArtifactResolutionException e) {
            // ignore
        } catch (IOException e) {
            // ignore
        }

        return false;
    }

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Inject
    ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Inject
    private ArtifactResolver resolver;
}
