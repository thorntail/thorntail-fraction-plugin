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
package org.wildfly.swarm.plugin.bom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.DependencyMetadata;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.FractionRegistry;

@Mojo(name = "generate-bom",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class BomMojo extends AbstractFractionsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<FractionMetadata> allFractions = fractions();

        Collection<FractionMetadata> fractions = null;

        if (this.stabilityIndex != null) {
            this.stabilityIndex = this.stabilityIndex.trim();
            if (this.stabilityIndex.equals("*")) {
                fractions = allFractions;
            } else if (this.stabilityIndex.endsWith("+")) {
                int level = Integer.parseInt(this.stabilityIndex.substring(0, this.stabilityIndex.length() - 1));
                fractions = allFractions
                        .stream()
                        .filter((e) -> e.getStabilityIndex().ordinal() >= level)
                        .collect(Collectors.toSet());
            } else {
                int level = Integer.parseInt(this.stabilityIndex);
                fractions = allFractions
                        .stream()
                        .filter((e) -> e.getStabilityIndex().ordinal() == level)
                        .collect(Collectors.toSet());
            }
        }

        List<DependencyMetadata> bomItems = new ArrayList<>();
        bomItems.addAll(fractions);
        bomItems.addAll(FractionRegistry.INSTANCE.bomInclusions());
        if (!this.product) {
            bomItems.add(arquillianFraction(this.project.getVersion()));
        }

        final Path bomPath = Paths.get(this.project.getBuild().getOutputDirectory(), "bom.pom");
        try {
            Files.createDirectories(bomPath.getParent());
            Files.write(bomPath,
                        BomBuilder.generateBOM(this.project, readTemplate(), bomItems).getBytes());
        } catch (IOException e) {
            throw new MojoFailureException("Failed to write bom.pom", e);
        }

        getLog().info(String.format("Wrote bom to %s", bomPath));

        for (FractionMetadata each : fractions) {
            getLog().info(String.format("%20s:%s", each.getGroupId(), each.getArtifactId()));
        }

        project.setFile(bomPath.toFile());

        getPluginContext().put("STABILITY_INDEX", this.stabilityIndex);
    }

    private String readTemplate() throws MojoFailureException {
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
    private String stabilityIndex;

    @Parameter(defaultValue = "${swarm.product.build}")
    private boolean product;

    @Parameter
    private File template;

}
