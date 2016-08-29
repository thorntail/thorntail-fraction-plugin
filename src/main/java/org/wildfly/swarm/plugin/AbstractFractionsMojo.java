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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractFractionsMojo extends AbstractMojo {



    protected static final String FRACTION_TAGS_PROPERTY_NAME = "swarm.fraction.tags";

    protected static final String FRACTION_INTERNAL_PROPERTY_NAME = "swarm.fraction.internal";

    private static List<MavenProject> PROBABLY_FRACTIONS = null;

    public List<MavenProject> probablyFractionProjects() {
        if ( PROBABLY_FRACTIONS == null ) {

            PROBABLY_FRACTIONS = this.project.getDependencyManagement().getDependencies()
                    .stream()
                    .filter(this::isSwarmProject)
                    .map(this::toProject)
                    .collect(Collectors.toList());
        }

        return PROBABLY_FRACTIONS;
    }

    public synchronized  Set<FractionMetadata> fractions() {
        return probablyFractionProjects()
                .stream()
                .map(FractionRegistry.INSTANCE::of)
                .filter(e->e!=null)
                .collect(Collectors.toSet());
    }

    protected MavenProject toProject(Dependency dependency) {
        try {
            return project(dependency);
        } catch (ProjectBuildingException e) {
            return null;
        }
    }

    protected MavenProject project(final Dependency dependency) throws ProjectBuildingException {
        final ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        request.setProcessPlugins(false);
        request.setSystemProperties(System.getProperties());
        request.setRemoteRepositories(this.project.getRemoteArtifactRepositories());
        request.setRepositorySession(this.repositorySystemSession);
        request.setResolveDependencies(true);
        final Artifact artifact =
                new org.apache.maven.artifact.DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                                                              dependency.getVersion(), "compile", "", "",
                                                              new DefaultArtifactHandler());
        return projectBuilder.build(artifact, request).getProject();
    }

    protected boolean isSwarmProject(Dependency dependency) {
        return dependency.getGroupId().startsWith( "org.wildfly.swarm" );
    }

    @Inject
    public ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    public DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    public MavenProject project;

}
