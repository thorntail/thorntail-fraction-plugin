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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        final Map<String, String> versions = parseModules();
        final Map<String, List<ExposedComponent>> components = resolveComponents(versions);
        final List<File> sourceArtifacts;
        try {
            sourceArtifacts = versions.keySet().stream()
                    .flatMap(module -> components.get(module).stream()
                            .filter(d -> d.doc != null)
                            .map(d -> {
                                return resolveArtifact(BomBuilder.SWARM_GROUP, d.doc, versions.get(module), "sources", "jar");
                            }))
                    .collect(Collectors.toList());
        } catch (ArtifactResolutionRuntimeException e){
            throw new MojoFailureException("Failed to resolve sources artifact", e.getCause());
        }

        this.sourceOutputDir.mkdirs();

        for (File each : sourceArtifacts) {
            ShrinkWrap.createFromZipFile(JavaArchive.class, each)
                    .as(ExplodedExporter.class)
                    .exportExploded(this.sourceOutputDir, ".");
        }

    }

    @Parameter
    private File sourceOutputDir;
}

