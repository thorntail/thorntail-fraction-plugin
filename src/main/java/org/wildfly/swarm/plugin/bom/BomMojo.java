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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.DependencyMetadata;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.FractionRegistry;

@Mojo(name = "generate-bom",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class BomMojo extends AbstractFractionsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<FractionMetadata> allFractions = fractions()
                .stream()
                .filter(f -> !f.isInternal())
                .collect(Collectors.toSet());

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
        bomItems.add(new DependencyMetadata("org.wildfly.swarm", "fraction-metadata", this.project.getVersion(), null, "jar", "compile"));
        bomItems.addAll(fractions);
        bomItems.addAll(FractionRegistry.INSTANCE.bomInclusions());
        if (!this.product) {
            bomItems.add(arquillianFraction(this.project.getVersion()));
        }

        final Path bomPath = Paths.get(this.project.getBuild().getOutputDirectory(), this.outputFile);
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

        try {
            List<RemoteRepository> repos = new ArrayList<>();
            for (ArtifactRepository remoteRepository : this.remoteRepositories) {
                repos.add(new RemoteRepository.Builder(
                        remoteRepository.getId(),
                        "default",
                        remoteRepository.getUrl()
                ).build());

            }

            final Path m2Repo = Paths.get(this.project.getBuild().getDirectory(), "m2repo");
            Files.createDirectories(m2Repo);
            fractions.stream()
                    .flatMap(e -> Stream.concat(
                            e.getDependencies().stream(),
                            e.getTransitiveDependencies().stream())
                    )
                    .distinct()
                    .flatMap(e -> Stream.of(
                            new DefaultArtifact(
                                    e.getGroupId(),
                                    e.getArtifactId(),
                                    null,
                                    "pom",
                                    e.getVersion()
                            ),
                            new DefaultArtifact(
                                    e.getGroupId(),
                                    e.getArtifactId(),
                                    e.getClassifier(),
                                    e.getPackaging(),
                                    e.getVersion()
                            ),
                            new DefaultArtifact(
                                    e.getGroupId(),
                                    e.getArtifactId(),
                                    "sources",
                                    e.getPackaging(),
                                    e.getVersion()
                            )))
                    .forEach(artifact -> {
                        ArtifactRequest request = new ArtifactRequest(artifact,
                                                                      repos,
                                                                      null);

                        try {
                            ArtifactResult result = resolver.resolveArtifact(session, request);
                            Path artifactPath = result.getArtifact().getFile().toPath();
                            Path localPath = m2Repo.resolve(Paths.get(this.session.getLocalRepositoryManager().getPathForLocalArtifact(result.getArtifact())));
                            Files.createDirectories(localPath.getParent());
                            Files.copy(artifactPath, localPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (ArtifactResolutionException | IOException e1) {
                            if (!artifact.getClassifier().equals("sources")) {
                                e1.printStackTrace();
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

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

    @Parameter(alias = "outputFile", defaultValue = "bom.pom")
    private String outputFile;

    @Parameter
    private File template;

    @Inject
    protected ArtifactResolver resolver;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession session;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(defaultValue = "${maven.repo.local}", readonly = true)
    protected String localRepo;


}
