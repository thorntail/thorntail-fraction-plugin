/**
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.swarm.plugin.utils.ChecksumUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 8/7/18
 */
public class MavenDependenciesResolver {

    private final Log log;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final ProjectDependenciesResolver dependencyResolver;
    private List<RemoteRepository> repositories;
    private List<ArtifactRepository> artifactRepositories;

    private final Set<String> appropriateScopes = new HashSet<>(asList("compile", "runtime"));

    private final ProjectBuilder projectBuilder;

    public MavenDependenciesResolver(Log log,
                                     RepositorySystem repositorySystem,
                                     ProjectDependenciesResolver dependencyResolver,
                                     List<ArtifactRepository> remoteRepositories,
                                     RepositorySystemSession session,
                                     ProjectBuilder projectBuilder) {
        this.log = log;
        this.repositorySystem = repositorySystem;
        this.dependencyResolver = dependencyResolver;
        this.session = session;
        this.repositories = RepositoryUtils.prepareRepositories(remoteRepositories);
        this.artifactRepositories = remoteRepositories;
        this.projectBuilder = projectBuilder;
    }

    public Set<MavenDependencyData> gatherTransitiveDependencies(MavenProject fractionProject)
            throws DependencyResolutionException, ProjectBuildingException, IOException {

        MavenProject project = mockProjectDependingOn(fractionProject);

        DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(project);
        request.setRepositorySession(session);
        DependencyResolutionResult resolutionResult = dependencyResolver.resolve(request);

        List<Dependency> dependencies = resolutionResult.getDependencies();
        Set<MavenDependencyData> result = dependencies.stream()
                .filter(d -> d.getArtifact().getFile().getName().endsWith(".jar"))
                .filter(d -> appropriateScopes.contains(d.getScope()))
                .map(org.eclipse.aether.graph.Dependency::getArtifact)
                .map(MavenDependencyData::new)
                .collect(Collectors.toSet());

        result.forEach(this::addCheckSum);
        return result;
    }

    protected MavenProject mockProjectDependingOn(MavenProject fractionProject) throws ProjectBuildingException, IOException {
        File pomFile = mockPom(fractionProject);

        return createProjectFromPom(pomFile);
    }

    private MavenProject createProjectFromPom(File pomFile) throws ProjectBuildingException {
        ProjectBuildingRequest projectRequest = new DefaultProjectBuildingRequest();
        projectRequest.setResolveDependencies(false);
        projectRequest.setRemoteRepositories(artifactRepositories);
        projectRequest.setProcessPlugins(false);
        projectRequest.setRepositorySession(session);
        ProjectBuildingResult result = projectBuilder.build(pomFile, projectRequest);
        return result.getProject();
    }

    private File mockPom(MavenProject project) throws IOException {
        File pom = File.createTempFile("pom", ".xml");
        pom.deleteOnExit();
        InputStream templateStream = getClass().getResourceAsStream("/pom-template.xml");
        try (InputStreamReader streamReader = new InputStreamReader(templateStream);
             BufferedReader reader = new BufferedReader(streamReader);
             FileWriter writer = new FileWriter(pom)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.replace("FRACTION_GROUP_ID", project.getGroupId());
                line = line.replace("FRACTION_ARTIFACT_ID", project.getArtifactId());
                line = line.replace("FRACTION_VERSION", project.getVersion());
                writer.write(line);
            }
        }
        return pom;
    }

    private void addCheckSum(MavenDependencyData dependencyData) {
        try {
            String checkSum = null;
            File dependencyFile = getFileForArtifact(dependencyData);
            checkSum = ChecksumUtil.calculateChecksum(dependencyFile);
            dependencyData.setCheckSum(checkSum);
        } catch (IOException | NoSuchAlgorithmException | ArtifactResolutionException e) {
            log.error("failed to get checksum for " + dependencyData, e);
        }

    }

    private File getFileForArtifact(MavenDependencyData dependencyData) throws ArtifactResolutionException {
        ArtifactRequest request = new ArtifactRequest(dependencyData.getArtifact(), repositories, null);
        ArtifactResult result = repositorySystem.resolveArtifact(session, request);
        return result.getArtifact().getFile();
    }
}
