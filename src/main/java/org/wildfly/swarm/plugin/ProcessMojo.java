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

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.impl.ArtifactResolver;

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
        getLog().info( "==== Bootstrap marker");
        executeBootstrapMarker();
        getLog().info( "==== Module filler");
        executeModuleFiller();
        getLog().info( "==== Jandexer");
        executeJandexer();
    }

    protected void executeBootstrapMarker() throws MojoExecutionException {

        BootstrapMarker bootstrapMarker = new BootstrapMarker(
                getLog(),
                this.project
        );

        try {
            bootstrapMarker.execute();
        } catch (IOException e) {
            throw new MojoExecutionException( "Unable to execute bootstrap marker", e );
        }

    }

    protected void executeModuleFiller() throws MojoExecutionException {

        ModuleFiller moduleFiller = new ModuleFiller(
                getLog(),
                this.repositorySystemSession,
                this.resolver,
                this.project);

        try {
            moduleFiller.execute();
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to execute module filler", e);
        }

    }

    protected void executeJandexer() throws MojoExecutionException {
        Jandexer jandexer = new Jandexer(
                getLog(),
                new File(this.project.getBuild().getOutputDirectory())
        );

        try {
            jandexer.execute();
        } catch (IOException e) {
            throw new MojoExecutionException( "Unable to execute jandexer", e );
        }
    }

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Inject
    private ArtifactResolver resolver;
}
