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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.eclipse.aether.DefaultRepositorySystemSession;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractFractionsMojo extends AbstractMojo {

    private static List<MavenProject> PROBABLE_FRACTIONS = null;

    private List<MavenProject> probableFractionProjects() {
        if (PROBABLE_FRACTIONS == null) {

            final ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
            request.setProcessPlugins(false);
            request.setSystemProperties(System.getProperties());
            request.setRemoteRepositories(this.project.getRemoteArtifactRepositories());
            request.setRepositorySession(this.repositorySystemSession);
            request.setResolveDependencies(true);

            try {
                PROBABLE_FRACTIONS = this.projectBuilder
                        .build(Collections.singletonList(findRoot(this.project).getFile()), true, request)
                        .stream()
                        .filter(this::isNotArquillianArtifact)
                        .map(ProjectBuildingResult::getProject)
                        .collect(Collectors.toList());
            } catch (ProjectBuildingException e) {
                getLog().error(e);
            }
        }

        return PROBABLE_FRACTIONS;
    }

    protected synchronized Set<FractionMetadata> fractions() {
        return probableFractionProjects()
                .stream()
                .map(FractionRegistry.INSTANCE::of)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    protected MavenProject findRoot(MavenProject current) {
        if (current.getArtifactId().equals("wildfly-swarm")) {
            return current;
        }
        return findRoot(current.getParent());
    }

    protected boolean isSwarmProject(Dependency dependency) {
        return dependency.getGroupId().startsWith("org.wildfly.swarm");
    }

    protected boolean isNotArquillianArtifact(ProjectBuildingResult result) {
        return !result.getProject().getArtifactId().contains("arquillian");
    }

    protected FractionMetadata arquillianFraction(String version) {
        return new FractionMetadata("org.wildfly.swarm", "arquillian", version);
    }

    @Inject
    public ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    public DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    public MavenProject project;

}
