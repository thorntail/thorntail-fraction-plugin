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
package org.wildfly.swarm.plugin.doc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.FractionMetadata;

@Mojo(name = "fraction-reference",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class FractionReferenceMojo extends AbstractFractionsMojo {

    @Override
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<FractionMetadata> allFractions = fractions();
        allFractions.forEach(e -> {
            try {
                generateReference(e);
            } catch (ArtifactResolutionException | IOException e1) {
                e1.printStackTrace();
            }
        });

        try {
            generateIndex(allFractions);
        } catch (FileNotFoundException e) {
            throw new MojoFailureException("unable to generate index", e);
        }
    }

    private void generateIndex(Set<FractionMetadata> allFractions) throws FileNotFoundException {
        Path output = this.project.getBasedir().toPath().resolve("index.adoc");

        try (PrintStream writer = new PrintStream(new FileOutputStream(output.toFile()))) {
            List<FractionMetadata> fractions = new ArrayList<>();
            fractions.addAll(allFractions);

            Collections.sort(fractions,
                             Comparator.comparing(l -> simplifyName(l.getArtifactId())));

            String[] previousParts = null;
            int previousOffset = 0;

            for (FractionMetadata fraction : fractions) {
                String[] parts = simplifyName(fraction.getArtifactId()).split("-");
                int offset = determineOffset(previousParts, previousOffset, parts);
                writer.println("include::fractions/" + fraction.getArtifactId() + ".adoc[leveloffset=+" + offset + "]");
                writer.println();

                previousParts = parts;
                previousOffset = offset;
            }
        }
    }

    protected int determineOffset(String[] previousParts, int previousOffset, String[] parts) {
        if (previousParts == null) {
            return 0;
        }

        if (parts.length == previousParts.length) {
            return previousOffset;
        }

        int offset = 0;
        for (int i = 0; i < parts.length; ++i) {
            if (previousParts.length > i) {
                if (parts[i].equals(previousParts[i])) {
                    ++offset;
                }
            } else {
                break;
            }
        }

        return offset;
    }

    protected String simplifyName(String name) {
        // damnit Camel
        if (name.endsWith("-core")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private void generateReference(FractionMetadata fraction) throws ArtifactResolutionException, IOException {
        File artifact = resolveArtifact(fraction.getGroupId(),
                                        fraction.getArtifactId(),
                                        fraction.getVersion(),
                                        null,
                                        "jar");

        Path output = this.project.getBasedir().toPath().resolve("fractions").resolve(fraction.getArtifactId() + ".adoc");

        Files.createDirectories(output.getParent());

        try (JarFile jar = new JarFile(artifact)) {
            try (PrintStream writer = new PrintStream(new FileOutputStream(output.toFile()))) {
                ZipEntry readme = jar.getEntry("META-INF/README.adoc");
                if (readme != null) {
                    try (BufferedReader readmeStream = new BufferedReader(new InputStreamReader(jar.getInputStream(readme)))) {
                        readmeStream.lines().forEach(writer::println);
                    }
                } else {
                    writer.println("= " + fraction.getName());
                }
                writer.println();

                //writer.println("== Coordinates");
                writer.println();
                writer.println(".Maven Coordinates");
                writer.println("[source,xml]");
                writer.println("----");
                writer.println("<dependency>");
                writer.println("  <groupId>" + fraction.getGroupId() + "</groupId>");
                writer.println("  <artifactId>" + fraction.getArtifactId() + "</artifactId>");
                writer.println("</dependency>");
                writer.println("----");
                writer.println();

                ZipEntry ref = jar.getEntry("META-INF/configuration-meta.properties");
                if (ref != null) {
                    Properties props = new Properties();
                    props.load(jar.getInputStream(ref));
                    props.remove("fraction");
                    if (props.size() > 0) {
                        writer.println(".Configuration");
                        writer.println();
                        List<String> names = new ArrayList<>();
                        names.addAll(props.stringPropertyNames());
                        Collections.sort(names);

                        names.forEach(name -> {
                            if (!name.equals("fraction")) {
                                writer.println(name.replace("*", "_KEY_") + ":: ");
                                writer.println(props.getProperty(name));
                                writer.println();
                            }
                        });
                    }
                }

                writer.println();
            }
        }
    }

    private File resolveArtifact(final String group,
                                 final String name,
                                 final String version,
                                 final String classifier,
                                 final String type) throws ArtifactResolutionException {
        final DefaultArtifact artifact = new DefaultArtifact(group, name, classifier, type, version);
        final LocalArtifactResult localResult = this.repositorySystemSession.getLocalRepositoryManager()
                .find(this.repositorySystemSession, new LocalArtifactRequest(artifact, this.remoteRepositories, null));
        File file = null;

        if (localResult.isAvailable()) {
            file = localResult.getFile();
        } else {
            final ArtifactResult result;
            result = resolver.resolveArtifact(this.repositorySystemSession,
                                              new ArtifactRequest(artifact, this.remoteRepositories, null));
            if (result.isResolved()) {
                file = result.getArtifact().getFile();
            }
        }

        return file;
    }

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Inject
    private ArtifactResolver resolver;
}

