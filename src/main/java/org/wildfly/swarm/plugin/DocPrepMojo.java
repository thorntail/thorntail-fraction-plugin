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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

@Mojo(name = "prep-doc-source",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class DocPrepMojo extends AbstractExposedComponentsMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.sourceOutputDir.mkdirs();

        try {
            parsedModules().keySet()
                    .forEach(module -> resolvedComponents().get(module).stream()
                            .filter(d -> d.doc != null)
                            .forEach(d -> ShrinkWrap.createFromZipFile(JavaArchive.class,
                                                                       resolveArtifact(BomBuilder.SWARM_GROUP, d.doc,
                                                                                       parsedModules().get(module),
                                                                                       "sources", "jar"))
                                    .as(ExplodedExporter.class)
                                    .exportExploded(this.sourceOutputDir, d.moduleName)));
        } catch (ArtifactResolutionRuntimeException e){
            throw new MojoFailureException("Failed to resolve sources artifact", e.getCause());
        }
    }

    @Parameter
    private File sourceOutputDir;
}

