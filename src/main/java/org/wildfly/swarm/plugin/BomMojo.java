/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generate-bom",
        defaultPhase = LifecyclePhase.PACKAGE)
public class BomMojo extends AbstractExposedComponentsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Map<String, String> versions = parseModules();

        final Map<String, List<ExposedComponent>> componentMap = resolveComponents(versions);

        verifyBomDependencies(BomBuilder.dependenciesList(versions, componentMap));

        final Path bomPath = Paths.get(this.project.getBuild().getOutputDirectory(), "bom.pom");
        try {
            Files.write(bomPath,
                        BomBuilder.generateBOM(readTemplate(), versions, componentMap).getBytes());
        } catch (IOException e) {
            throw new MojoFailureException("Failed to write bom.pom", e);
        }

        getLog().info(String.format("Wrote bom to %s", bomPath));
    }

    protected void verifyBomDependencies(final List<String> deps) throws MojoFailureException {
        for (String dep : deps) {
            final String[] parts = dep.split(":");
            try {
                resolveArtifact(parts[0], parts[1], parts[2], null, "pom");
            } catch (ArtifactResolutionRuntimeException e) {
                throw new MojoFailureException(String.format("%s does not resolvable", dep), e.getCause());
            }
        }
    }

    protected String readTemplate() throws MojoFailureException {
        if (this.template == null) {

            throw new MojoFailureException("No template specified");
        }
        try {

            return new String(Files.readAllBytes(this.template.toPath()), "UTF-8");
        } catch (IOException e) {

            throw new MojoFailureException("Failed to read template " + this.template, e);
        }
    }

    @Parameter
    private File template;

}
