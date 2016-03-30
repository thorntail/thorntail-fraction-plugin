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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

public abstract class AbstractExposedComponentsMojo extends AbstractMojo {

    // borrowed from https://github.com/forge/furnace/blob/master/manager/resolver/maven/src/main/java/org/jboss/forge/furnace/manager/maven/MavenContainer.java#L216
    protected static org.eclipse.aether.repository.Proxy convertFromMavenProxy(Proxy proxy) {
        if (proxy != null) {
            return  new org.eclipse.aether.repository.Proxy(proxy.getProtocol(),
                                                            proxy.getHost(),
                                                            proxy.getPort(),
                                                            new AuthenticationBuilder()
                                                                    .addUsername(proxy.getUsername())
                                                                    .addPassword(proxy.getPassword())
                                                                    .build());
        }

        return null;
    }

    protected File resolveArtifact(final String group,
                                   final String name,
                                   final String version,
                                   final String classifier,
                                   final String type) throws ArtifactResolutionRuntimeException {
        final List<RemoteRepository> aetherRepos = this.remoteRepositories.stream()
                .map(r -> buildRemoteRepository(r.getId(),
                                                r.getUrl(),
                                                r.getAuthentication()))
                .collect(Collectors.toList());
        final DefaultArtifact artifact = new DefaultArtifact(group, name, classifier, type, version);
        final LocalArtifactResult localResult = this.repositorySystemSession.getLocalRepositoryManager()
                .find(this.repositorySystemSession, new LocalArtifactRequest(artifact, aetherRepos, null));
        File file = null;

        if (localResult.isAvailable()) {
            file = localResult.getFile();
        } else {
            final ArtifactResult result;
            try {
                result = resolver.resolveArtifact(this.repositorySystemSession,
                                                  new ArtifactRequest(artifact, aetherRepos, null));
            } catch (ArtifactResolutionException e) {
                throw new ArtifactResolutionRuntimeException(e);
            }
            if (result.isResolved()) {
                file = result.getArtifact().getFile();
            }
        }

        return file;
    }

    protected RemoteRepository buildRemoteRepository(final String id, final String url, final Authentication auth) {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(id, "default", url);
        if (auth != null &&
                auth.getUsername() != null &&
                auth.getPassword() != null) {
            builder.setAuthentication(new AuthenticationBuilder()
                                              .addUsername(auth.getUsername())
                                              .addPassword(auth.getPassword()).build());
        }

        builder.setProxy(convertFromMavenProxy(this.mavenSession.getSettings().getActiveProxy()));

        return builder.build();
    }

    protected Map<String, String> parseModules() {
        final Map<String, String> versions = new HashMap<>();
        this.modules.forEach(c -> {
            String[] parts = c.split(":");
            versions.put(parts[0], parts[1]);
        });

        return versions;
    }

    protected Map<String, List<ExposedComponent>> resolveComponents(final Map<String, String> versions) {
        final Map<String, List<ExposedComponent>> componentMap = new HashMap<>();
        versions.forEach((k, v) -> componentMap.put(k, resolveComponentDescriptor(k, v)));

        return componentMap;
    }

    protected List<ExposedComponent> resolveComponentDescriptor(final String name, final String version) {
        File descriptorFile = null;
        try {
            descriptorFile = resolveArtifact(BomBuilder.SWARM_GROUP, name, version, "exposed-components", "json");
        } catch (ArtifactResolutionRuntimeException e) {
            throw new RuntimeException(String.format("Failed to locate exposed-components.json for %s:%s", name, version),
                                       e.getCause());
        }

        try {
            return descriptorFile != null ? ExposedComponent.parseDescriptor(descriptorFile.toURI().toURL()) : null;
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Failed to read exposed-components.json for %s:%s", name, version), e);
        }
    }

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private List<ArtifactRepository> remoteRepositories;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Inject
    private ArtifactResolver resolver;

    @Parameter
    private List<String> modules = new ArrayList<>();

    static class ArtifactResolutionRuntimeException extends RuntimeException {
        public ArtifactResolutionRuntimeException(ArtifactResolutionException cause) {
            super(cause);
        }
    }
}
