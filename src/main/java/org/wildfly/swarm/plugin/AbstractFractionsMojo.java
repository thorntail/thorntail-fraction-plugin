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

    private static final String DEFAULT_STABILITY_INDEX = "2";

    protected static final String FRACTION_STABILITY_PROPERTY_NAME = "swarm.fraction.stability";

    protected static final String FRACTION_TAGS_PROPERTY_NAME = "swarm.fraction.tags";

    protected static final String FRACTION_INTERNAL_PROPERTY_NAME = "swarm.fraction.internal";

    public List<MavenProject> fractions() {
        return fractions(null);
    }

    public List<MavenProject> fractions(Integer stabilityIndex) {
        return this.project.getDependencyManagement().getDependencies()
                .stream()
                .filter(this::mightBeFraction)
                .map(this::toProject)
                .filter(e -> e != null)
                .filter(this::isFraction)
                .filter(e->{
                    if ( stabilityIndex == null ) {
                        return true;
                    }

                    return isAtLeast( e, stabilityIndex );
                })
                .collect(Collectors.toList());
    }

    protected boolean isAtLeast(MavenProject project, int index) {
        return Integer.parseInt(project.getProperties().getProperty( FRACTION_STABILITY_PROPERTY_NAME, DEFAULT_STABILITY_INDEX )) >= index;
    }

    protected MavenProject toProject(Dependency dependency) {
        try {
            return project(dependency);
        } catch (ProjectBuildingException e) {
            return null;
        }
    }

    protected MavenProject project(Dependency dependency) throws ProjectBuildingException {
        ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
        request.setProcessPlugins(false);
        request.setSystemProperties(System.getProperties());
        request.setRemoteRepositories(this.project.getRemoteArtifactRepositories());
        request.setRepositorySession(this.repositorySystemSession);
        request.setResolveDependencies(true);
        Artifact artifact =
                new org.apache.maven.artifact.DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                        dependency.getVersion(), "compile", "", "",
                        new DefaultArtifactHandler());
        MavenProject project = projectBuilder.build(artifact, request).getProject();

        return project;
    }

    protected boolean mightBeFraction(Dependency dependency) {
        return dependency.getGroupId().startsWith( "org.wildfly.swarm" );
    }

    protected boolean isFraction(MavenProject project) {
        return project.getProperties().getProperty(FRACTION_STABILITY_PROPERTY_NAME) != null
                || project.getProperties().getProperty(FRACTION_TAGS_PROPERTY_NAME) != null
                || project.getProperties().getProperty(FRACTION_INTERNAL_PROPERTY_NAME ) != null;
    }

    @Inject
    public ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    public DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    public MavenProject project;
}
