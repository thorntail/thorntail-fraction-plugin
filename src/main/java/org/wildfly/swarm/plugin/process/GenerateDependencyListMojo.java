/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.plugin.process;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.swarm.plugin.MavenDependenciesResolver;
import org.wildfly.swarm.plugin.MavenDependencyData;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@Mojo(
        name = "generate-dependency-list",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class GenerateDependencyListMojo extends AbstractMojo {

    public void execute() {
        Set<MavenDependencyData> deps = mavenDepenendencies();
        Path file = Paths.get(this.project.getBuild().getOutputDirectory(), "META-INF", "maven-dependencies.txt");
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = new FileWriter(file.toFile());
                 PrintWriter writer = new PrintWriter(out)) {
                deps.stream()
                        .map(MavenDependencyData::toString)
                        .forEach(writer::println);
            }
        } catch (IOException e) {
            getLog().error(e.getMessage(), e);
        }
    }

    private Set<MavenDependencyData> mavenDepenendencies() {
        MavenDependenciesResolver dependenciesResolver =
                new MavenDependenciesResolver(
                        getLog(),
                        repositorySystem,
                        projectDependenciesResolver,
                        remoteRepositories,
                        repositorySystemSession,
                        projectBuilder);
        try {
            return dependenciesResolver.gatherTransitiveDependencies(this.project);
        } catch (DependencyResolutionException | ProjectBuildingException | IOException e) {
            getLog().error("failed to resolve dependencies for project", e);
            return null;
        }
    }

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Component
    protected RepositorySystem repositorySystem;

    @Inject
    private ProjectBuilder projectBuilder;

    @Inject
    private ProjectDependenciesResolver projectDependenciesResolver;
}
