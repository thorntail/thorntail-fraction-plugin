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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.DependencyMetadata;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.FractionRegistry;

@Mojo(name = "generate-certified-bom",
        defaultPhase = LifecyclePhase.PACKAGE)
public class CertifiedBomMojo extends AbstractFractionsMojo {

    @Override
    public void execute() throws MojoFailureException {
        Set<FractionMetadata> certifiedFractions = loadCertifiedMetadata();

        List<DependencyMetadata> bomItems = new ArrayList<>();
        bomItems.addAll(certifiedFractions);
        bomItems.add(arquillianFraction(certifiedVersion));

        final Path bomPath = Paths.get(this.project.getBuild().getOutputDirectory(), "bom.pom");
        try {
            Files.createDirectories(bomPath.getParent());
            Files.write(bomPath,
                        BomBuilder.generateBOM(this.project, readTemplate(), bomItems).getBytes());
        } catch (IOException e) {
            throw new MojoFailureException("Failed to write bom.pom", e);
        }

        getLog().info(String.format("Wrote bom to %s", bomPath));

        for (FractionMetadata each : certifiedFractions) {
            getLog().info(String.format("%20s:%s", each.getGroupId(), each.getArtifactId()));
        }

        project.setFile(bomPath.toFile());
    }

    protected Set<FractionMetadata> loadCertifiedMetadata() throws MojoFailureException {
        Path certifiedConf = new File(this.project.getBasedir(), "certified.conf").toPath();
        try (BufferedReader in = new BufferedReader(new FileReader(certifiedConf.toFile()))) {
            return in.lines()
                    .filter(artifact -> !"arquillian".equals(artifact))
                    .map(artifact -> new FractionMetadata(FractionRegistry.THORNTAIL_GROUP_ID, artifact, certifiedVersion))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new MojoFailureException("Unable to read certified.conf", e);
        }
    }

    private String readTemplate() throws MojoFailureException {
        if (this.template == null) {
            throw new MojoFailureException("No template specified");
        }

        try {
            return new String(Files.readAllBytes(this.template.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoFailureException("Failed to read template " + this.template, e);
        }
    }

    @Parameter(defaultValue = "${project}", readonly = true)
    public MavenProject project;

    @Parameter
    private File template;

    @Parameter
    private String certifiedVersion;

}
