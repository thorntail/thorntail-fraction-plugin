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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

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
        Map<String, Fraction> fractions = fractions();

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
}
