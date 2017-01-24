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
package org.wildfly.swarm.plugin.process;

import java.io.File;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactResolver;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.FractionRegistry;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(
        name = "process",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class ProcessMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        FractionMetadata meta = FractionRegistry.INSTANCE.of(this.project);

        if (meta == null) {
            return;
        }

        new ModuleGenerator(getLog(), this.project).apply(meta);
        new CDIMarker(getLog(), this.project).apply(meta);
        new ModuleFiller(getLog(), this.repositorySystemSession, this.resolver, this.project).apply(meta);
        new FractionManifestGenerator(getLog(), this.project).apply(meta);
        new Jandexer(getLog(), new File(this.project.getBuild().getOutputDirectory())).apply(meta);
    }

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    protected RepositorySystem repositorySystem;

    @Inject
    private ArtifactResolver resolver;

    private FractionMetadata manifest;
}
