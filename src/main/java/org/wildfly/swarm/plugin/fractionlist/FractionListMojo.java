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
package org.wildfly.swarm.plugin.fractionlist;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.FractionMetadata;

import static java.nio.file.Files.copy;

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
public class FractionListMojo extends AbstractFractionsMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<FractionMetadata> fractions = fractions();

        generateTxt(fractions);
        generateJSON(fractions);
        generateJavascript(fractions);
        extractDetectors(fractions);
    }

    private void generateTxt(Set<FractionMetadata> fractions) {

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.txt");

        outFile.getParentFile().mkdirs();

        try (FileWriter out = new FileWriter(outFile)) {
            for (FractionMetadata each : fractions) {
                out.write(each.toString());
                out.write(" = ");
                out.write(each.getDependenciesString());
                out.write("\n");
            }
        } catch (IOException e) {
            getLog().error(e);
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

    private void generateJSON(Set<FractionMetadata> fractions) {
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

    private void generateJavascript(Set<FractionMetadata> fractions) {
        ObjectMapper mapper = new ObjectMapper();

        mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        File outFile = new File(this.project.getBuild().getOutputDirectory(), "fraction-list.js");

        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write("swarmVersion='" + swarmVersion + "';");
            writer.write("fractionList = ");
            writer.flush();

            mapper.writeValue(writer, fractions);

            writer.write(";");
            writer.flush();
        } catch (IOException e) {
            getLog().error(e);
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

    private void extractDetectors(Set<FractionMetadata> fractions) {
        Path buildDir = Paths.get(this.project.getBuild().getOutputDirectory());

        Set<FractionMetadata> fractionsWithDetectors = fractions.stream()
                .filter(f -> f.getDetectorClasses().size() > 0)
                .collect(Collectors.toSet());

        fractionsWithDetectors
                .forEach(f -> {
                    f.getDetectorClasses().forEach((relative, full) -> {
                        try {
                            Path target = buildDir.resolve(relative);
                            File targetFile = target.toFile();
                            if (!targetFile.exists()) {
                                targetFile.getParentFile().mkdirs();
                            }
                            copy(full, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            getLog().error(e);
                        }
                    });
                });

        String servicesFile = "META-INF" + File.separator + "services" + File.separator + "org.wildfly.swarm.spi.meta.FractionDetector";
        File outFile = new File(this.project.getBuild().getOutputDirectory(), servicesFile);
        if (!outFile.exists()) {
            outFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(outFile)) {
            fractionsWithDetectors
                    .stream()
                    .flatMap(f -> f.getDetectorClasses().keySet().stream())
                    .map(p -> p.toString().substring(0, p.toString().lastIndexOf('.')))
                    .map(s -> s.replace(File.separatorChar, '.'))
                    .forEach(p -> {
                        try {
                            writer.write(p);
                            writer.write("\n");
                        } catch (IOException e) {
                            getLog().error(e);
                        }
                    });

            writer.flush();
        } catch (IOException e) {
            getLog().error(e);
        }

    }

    @Parameter(defaultValue = "${project.version}", readonly = true)
    public String swarmVersion;

}
