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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public abstract class AbstractExposedComponentsMojo extends AbstractMojo {

    protected File resolveArtifact(final String group,
                                   final String name,
                                   final String version,
                                   final String classifier,
                                   final String type) throws ArtifactResolutionRuntimeException {
        final DefaultArtifact artifact = new DefaultArtifact(group, name, classifier, type, version);
        final LocalArtifactResult localResult = this.repositorySystemSession.getLocalRepositoryManager()
                .find(this.repositorySystemSession, new LocalArtifactRequest(artifact, this.remoteRepositories, null));
        File file = null;

        if (localResult.isAvailable()) {
            file = localResult.getFile();
        } else {
            final ArtifactResult result;
            try {
                result = resolver.resolveArtifact(this.repositorySystemSession,
                                                  new ArtifactRequest(artifact, this.remoteRepositories, null));
            } catch (ArtifactResolutionException e) {
                throw new ArtifactResolutionRuntimeException(String.format("%s:%s:%s:%s:%s", group, name, version,
                                                                           classifier, type), e);
            }
            if (result.isResolved()) {
                file = result.getArtifact().getFile();
            }
        }

        return file;
    }

    protected Map<String, String> parsedModules() {
        if (this.versions.isEmpty()) {
            this.modules.forEach(c -> {
                String[] parts = c.split(":");
                this.versions.put(parts[0], parts[1]);
            });
        }

        return this.versions;
    }

    protected List<ExposedComponents> resolvedComponents() {
        if (this.components.isEmpty()) {
            parsedModules().forEach((k, v) -> this.components.add(resolveComponentDescriptor(k, v)));
        }
        return this.components;
    }

    protected ExposedComponents resolveComponentDescriptor(final String name, final String version) {
        File descriptorFile = null;
        try {
            descriptorFile = resolveArtifact(BomBuilder.SWARM_GROUP, name, version, "exposed-components", "json");
        } catch (ArtifactResolutionRuntimeException e) {
            throw new RuntimeException(String.format("Failed to locate exposed-components.json for %s:%s", name, version),
                                       e.getCause());
        }

        try {
            return descriptorFile != null ? ExposedComponents.parseDescriptor(version, descriptorFile.toURI().toURL()) : null;
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Failed to read exposed-components.json for %s:%s", name, version), e);
        }
    }

    protected Dependency gavToDependency(final String gav) {
        final String[] parts = gav.split(":");
        final Dependency dep = new Dependency();
        dep.setGroupId(parts[0]);
        dep.setArtifactId(parts[1]);
        dep.setVersion(parts[2]);

        return dep;
    }

    protected List<Dependency> bomDependencies() {
        return BomBuilder.dependenciesList(resolvedComponents()).stream()
                .map(this::gavToDependency)
                .collect(Collectors.toList());
    }

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Inject
    private ArtifactResolver resolver;

    @Parameter
    private List<String> modules = new ArrayList<>();

    private Map<String, String> versions = new HashMap<>();

    private List<ExposedComponents> components = new ArrayList<>();

    static class ArtifactResolutionRuntimeException extends RuntimeException {
        public ArtifactResolutionRuntimeException(String gav, ArtifactResolutionException cause) {
            super(String.format("%s for: %s", cause.getMessage(), gav), cause);
        }
    }
}
