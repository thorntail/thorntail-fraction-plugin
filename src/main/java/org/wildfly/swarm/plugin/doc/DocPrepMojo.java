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
package org.wildfly.swarm.plugin.doc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.StabilityLevel;

@Mojo(name = "prep-doc-source",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class DocPrepMojo extends AbstractFractionsMojo {

    @Override
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (getPluginContext().containsKey(ALREADY_EXECUTED)) {
            getLog().info("DocPrepMojo already executed, skipping");

            return;
        }

        getPluginContext().put(ALREADY_EXECUTED, true);

        this.sourceOutputDir.mkdirs();
        final Map<String, String> extraModules = new HashMap<>();

        this.extraModules.forEach(s -> {
            final String[] parts = s.split(":");
            extraModules.put(parts[0], parts[1]);
        });

        this.project.getDependencyManagement().getDependencies()
                .stream()
                .filter(this::isSwarmProject)
                .filter(d -> extraModules.containsKey(d.getArtifactId()))
                .forEach(d -> exportSources(extraModules.get(d.getArtifactId()),
                                            d.getGroupId(),
                                            d.getArtifactId(),
                                            d.getVersion(),
                                            null));

        fractions()
                .forEach(fraction -> exportSources(fraction.getName(),
                                                   fraction.getGroupId(),
                                                   fraction.getArtifactId(),
                                                   fraction.getVersion(),
                                                   fraction.getStabilityIndex()));
    }

    private void exportSources(final String name,
                                 final String groupId,
                                 final String artifactId,
                                 final String version,
                                 final StabilityLevel stability) {
        final File destDir = new File(this.sourceOutputDir, artifactId);
        destDir.mkdirs();

        File srcJar = null;
        try {
            srcJar = resolveArtifact(groupId, artifactId, version, "sources", "jar");
        } catch (ArtifactResolutionException ignored) {
        }

        if (srcJar != null) {
            ShrinkWrap.createFromZipFile(JavaArchive.class, srcJar)
                    .as(ExplodedExporter.class)
                    .exportExploded(this.sourceOutputDir, artifactId);
        } else {
            getLog().warn(String.format("Failed to find sources for %s:%s:%s",
                                        groupId, artifactId, version));
        }

        try {
            if (destDir.listFiles().length > 0) {
                Files.write(Paths.get(destDir.getAbsolutePath(), "_metadata"),
                            String.format("%s::::%s",
                                          name,
                                          stability != null ? stability.name() : "")
                                    .getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File resolveArtifact(final String group,
                                   final String name,
                                   final String version,
                                   final String classifier,
                                   final String type) throws ArtifactResolutionException {
        final DefaultArtifact artifact = new DefaultArtifact(group, name, classifier, type, version);
        final LocalArtifactResult localResult = this.repositorySystemSession.getLocalRepositoryManager()
                .find(this.repositorySystemSession, new LocalArtifactRequest(artifact, this.remoteRepositories, null));
        File file = null;

        if (localResult.isAvailable()) {
            file = localResult.getFile();
        } else {
            final ArtifactResult result;
            result = resolver.resolveArtifact(this.repositorySystemSession,
                                              new ArtifactRequest(artifact, this.remoteRepositories, null));
            if (result.isResolved()) {
                file = result.getArtifact().getFile();
            }
        }

        return file;
    }

    private static final String ALREADY_EXECUTED = "DocPrepMojo-already-executed";

    @Parameter
    private File sourceOutputDir;

    @Parameter
    private List<String> extraModules;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Inject
    private ArtifactResolver resolver;
}

