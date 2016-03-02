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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ArtifactType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.DependenciesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleAliasDescriptor;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDependencyType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ResourcesType;
import org.jboss.shrinkwrap.descriptor.impl.jbossmodule13.ModuleAliasDescriptorImpl;
import org.jboss.shrinkwrap.descriptor.impl.jbossmodule13.ModuleDescriptorImpl;
import org.jboss.shrinkwrap.descriptor.spi.node.Node;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeImporter;
import org.jboss.shrinkwrap.descriptor.spi.node.dom.XmlDomNodeImporterImpl;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class GenerateMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            loadRewriteRules();
            Set<String> requiredModules = new HashSet<>();
            Set<String> availableModules = new HashSet<>();
            walkProjectModules(requiredModules, availableModules);
            walkDependencyModules(requiredModules, availableModules);

            Map<String, File> potentialModules = new HashMap<>();
            indexPotentialModules(potentialModules);

            locateFillModules(potentialModules, requiredModules, availableModules);
        } catch (IOException e) {
            throw new MojoFailureException("Unable to walk modules directory", e);
        }

        long size = 0;
        DecimalFormat fmt = new DecimalFormat("####.00");
        for (String each : this.allArtifacts) {
            ArtifactRequest req = new ArtifactRequest();
            org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(each);
            req.setArtifact(artifact);

            try {
                ArtifactResult artifactResult = this.resolver.resolveArtifact(repositorySystemSession, req);
                if (artifactResult.isResolved()) {
                    File file = artifactResult.getArtifact().getFile();
                    long artifactSize = Files.size(file.toPath());
                    size += artifactSize;
                    //getLog().info( "Artifact: " + each + ":  " + fmt.format( artifactSize / ( 1024.0*1024.0) ) + "mb");
                    getLog().info(String.format("%100s %10s mb", each, fmt.format(artifactSize / (1024.0 * 1024.0))));
                }
            } catch (ArtifactResolutionException e) {
                //e.printStackTrace();
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
        getLog().info(this.project.getArtifactId() + ": total size:  " + fmt.format(size / (1024.0 * 1024.0)) + " mb");
    }

    protected void loadRewriteRules() throws IOException {
        Path rewriteConf = Paths.get(this.project.getBasedir().getAbsolutePath(), "module-rewrite.conf");
        this.rules = new ModuleRewriteConf(rewriteConf);

    }

    protected void locateFillModules(Map<String, File> potentialModules, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoFailureException {

        int counter = 1;

        while (true) {
            Set<String> fillModules = new HashSet<>();
            fillModules.addAll(requiredModules);
            fillModules.removeAll(availableModules);

            if (fillModules.isEmpty()) {
                break;
            }

            Set<File> relevantFiles = new HashSet<>();
            for (String each : fillModules) {
                File file = potentialModules.get(each);
                if (file == null) {
                    throw new MojoFailureException("Unable to locate required module: " + each);
                }
                relevantFiles.add(file);
            }

            addFillModules(fillModules, relevantFiles, requiredModules, availableModules);
        }
    }

    protected void addFillModules(Set<String> fillModules, Set<File> relevantFiles, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoFailureException {
        for (File each : relevantFiles) {
            addFillModules(fillModules, each, requiredModules, availableModules);
        }
    }

    protected void addFillModules(Set<String> fillModules, File file, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoFailureException {
        Map<String, ZipEntry> moduleXmls = new HashMap<>();
        ZipEntry featurePackXml = null;

        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                String coreName = null;

                if (name.equals("wildfly-feature-pack.xml")) {
                    featurePackXml = entry;
                } else if (name.startsWith(MODULES_LAYERS_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_LAYERS_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                    coreName = coreName.substring(coreName.indexOf('/') + 1);
                } else if (name.startsWith(MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                }

                if (coreName != null) {
                    int lastSlashLoc = coreName.lastIndexOf('/');

                    String moduleName = coreName.substring(0, lastSlashLoc);
                    String slot = coreName.substring(lastSlashLoc + 1);

                    moduleName = moduleName.replace('/', '.');

                    if (fillModules.contains(moduleName + ":" + slot)) {
                        moduleXmls.put(moduleName + ":" + slot, entry);
                    }
                }
            }

            if (featurePackXml == null) {
                throw new MojoFailureException("Unable to find -feature-pack.xml");
            }

            Map<String, String> versions = processFeaturePackXml(zip.getInputStream(featurePackXml));

            for (String moduleName : moduleXmls.keySet()) {
                ZipEntry entry = moduleXmls.get(moduleName);
                addFillModule(versions, moduleName, zip.getInputStream(entry), requiredModules, availableModules);
                addResources(zip, moduleName, entry);
            }
        }
    }

    protected void addResources(ZipFile zip, String moduleName, ZipEntry moduleXml) {

        String moduleXmlPath = moduleXml.getName();
        String rootName = moduleXmlPath.substring(0, moduleXmlPath.length() - "module.xml".length());

        Enumeration<? extends ZipEntry> entries = zip.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                if (entry.getName().startsWith(rootName) && !entry.getName().equals(moduleXmlPath)) {

                    String resourceRelative = entry.getName().substring(rootName.length());
                    resourceRelative.replace('/', File.separatorChar);
                    Path classesDir = Paths.get(this.project.getBuild().getOutputDirectory());
                    Path modulesDir = classesDir.resolve("modules");

                    String[] parts = moduleName.split(":");
                    String[] moduleParts = parts[0].split("\\.");

                    Path moduleDir = modulesDir;

                    for (int i = 0; i < moduleParts.length; ++i) {
                        moduleDir = moduleDir.resolve(moduleParts[i]);
                    }

                    moduleDir = moduleDir.resolve(parts[1]);

                    Path resourcePath = moduleDir.resolve(resourceRelative);

                    try {
                        Files.createDirectories(resourcePath.getParent());
                        Files.copy(zip.getInputStream(entry), resourcePath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    protected void addFillModule(Map<String, String> versions, String moduleName, InputStream in, Set<String> requiredModules, Set<String> availableModules) throws IOException {

        Path classesDir = Paths.get(this.project.getBuild().getOutputDirectory());
        Path modulesDir = classesDir.resolve("modules");

        String[] parts = moduleName.split(":");
        String[] moduleParts = parts[0].split("\\.");

        Path moduleDir = modulesDir;

        for (int i = 0; i < moduleParts.length; ++i) {
            moduleDir = moduleDir.resolve(moduleParts[i]);
        }

        moduleDir = moduleDir.resolve(parts[1]);

        Path moduleXml = moduleDir.resolve("module.xml");

        processFillModule(versions, moduleXml, in);

        analyzeModuleXml(modulesDir, moduleXml, requiredModules, availableModules);
    }

    protected void processFillModule(Map<String, String> versions, Path moduleXml, InputStream in) throws IOException {
        Files.createDirectories(moduleXml.getParent());

        NodeImporter importer = new XmlDomNodeImporterImpl();
        Node node = importer.importAsNode(in, true);

        String rootName = node.getName();

        if (rootName.equals("module")) {
            ModuleDescriptor desc = new ModuleDescriptorImpl(null, node);
            List<ArtifactType<ResourcesType<ModuleDescriptor>>> artifacts = desc.getOrCreateResources().getAllArtifact();
            for (ArtifactType<ResourcesType<ModuleDescriptor>> artifact : artifacts) {
                String name = artifact.getName();
                if (name.startsWith("${")) {
                    name = name.substring(2, name.length() - 1);
                }
                if (name.endsWith("?jandex")) {
                    name = name.replace("?jandex", "");
                }

                name = versions.get(name);
                artifact.name(name);

                this.allArtifacts.add(name);
            }

            desc = this.rules.rewrite(desc);

            try (FileOutputStream out = new FileOutputStream(moduleXml.toFile())) {
                desc.exportTo(out);
            }
        } else if (rootName.equals("module-alias")) {
            ModuleAliasDescriptor desc = new ModuleAliasDescriptorImpl(null, node);
            try (FileOutputStream out = new FileOutputStream(moduleXml.toFile())) {
                desc.exportTo(out);
            }
        }
    }

    protected Map<String, String> processFeaturePackXml(InputStream in) throws IOException {
        Map<String, String> versions = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                Matcher matcher = ARTIFACT_PATTERN.matcher(line);
                if (matcher.matches()) {
                    MatchResult result = matcher.toMatchResult();

                    String groupId = result.group(1);
                    String artifactId = result.group(2);
                    String version = result.group(3);
                    String classifier = result.group(5);

                    String expr = groupId + ":" + artifactId + (classifier == null ? "" : "::" + classifier);
                    String qualified = groupId + ":" + artifactId + ":" + version + (classifier == null ? "" : ":" + classifier);

                    versions.put(expr, qualified);
                }
            }
        }
        return versions;
    }

    protected void indexPotentialModules(Map<String, File> potentialModules) throws IOException {
        Set<Artifact> artifacts = this.project.getArtifacts();

        for (Artifact each : artifacts) {
            if (each.getType().equals("zip")) {
                indexPotentialModules(each.getFile(), potentialModules);
            }
        }
    }

    protected void indexPotentialModules(File file, Map<String, File> potentialModules) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry each = entries.nextElement();
                String name = each.getName();
                String coreName = null;
                if (name.startsWith(MODULES_LAYERS_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_LAYERS_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                    coreName = coreName.substring(coreName.indexOf('/') + 1);
                } else if (name.startsWith(MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                }

                if (coreName != null) {
                    int lastSlashLoc = coreName.lastIndexOf('/');

                    String moduleName = coreName.substring(0, lastSlashLoc);
                    String slot = coreName.substring(lastSlashLoc + 1);

                    moduleName = moduleName.replace('/', '.');

                    potentialModules.put(moduleName + ":" + slot, file);
                }
            }
        }
    }

    protected void walkProjectModules(final Set<String> requiredModules, final Set<String> availableModules) throws IOException {
        List<Resource> resources = this.project.getBuild().getResources();
        for (Resource each : resources) {
            final Path modulesDir = Paths.get(each.getDirectory()).resolve("modules");
            if (Files.exists(modulesDir)) {
                Files.walkFileTree(modulesDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.getFileName().toString().equals("module.xml")) {
                            analyzeModuleXml(modulesDir, file, requiredModules, availableModules);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    protected void walkDependencyModules(Set<String> requiredModules, Set<String> availableModules) throws IOException {
        Set<Artifact> artifacts = this.project.getArtifacts();

        for (Artifact each : artifacts) {
            collectAvailableModules(each, availableModules);
        }
    }

    protected void collectAvailableModules(Artifact artifact, Set<String> modules) throws IOException {
        if (artifact.getType().equals("jar")) {
            try (JarFile jar = new JarFile(artifact.getFile())) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry each = entries.nextElement();
                    String name = each.getName();
                    if (name.startsWith(MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                        String coreName = name.substring(MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());

                        int lastSlashLoc = coreName.lastIndexOf('/');

                        String moduleName = coreName.substring(0, lastSlashLoc);
                        String slot = coreName.substring(lastSlashLoc + 1);

                        moduleName = moduleName.replace('/', '.');
                        modules.add(moduleName + ":" + slot);
                    }
                }
            }
        }
    }

    protected void analyzeModuleXml(Path root, Path moduleXml, Set<String> requiredModules, Set<String> availableModules) throws IOException {
        Path modulePath = root.relativize(moduleXml).getParent();


        String selfSlot = modulePath.getName(modulePath.getNameCount() - 1).toString();
        String selfModuleName = modulePath.getParent().toString().replace(File.separatorChar, '.');

        getLog().info("Analyzing: " + selfModuleName + ":" + selfSlot + " (" + moduleXml + ")");

        availableModules.add(selfModuleName + ":" + selfSlot);

        NodeImporter importer = new XmlDomNodeImporterImpl();
        Node node = importer.importAsNode(new FileInputStream(moduleXml.toFile()), true);

        String rootName = node.getName();

        if (rootName.equals("module")) {
            ModuleDescriptor desc = new ModuleDescriptorImpl(null, node);

            desc = this.rules.rewrite(desc);

            DependenciesType<ModuleDescriptor> dependencies = desc.getOrCreateDependencies();
            List<ModuleDependencyType<DependenciesType<ModuleDescriptor>>> moduleDependencies = dependencies.getAllModule();
            for (ModuleDependencyType<DependenciesType<ModuleDescriptor>> moduleDependency : moduleDependencies) {
                if (moduleDependency.isOptional()) {
                    continue;
                }
                String name = moduleDependency.getName();
                String slot = moduleDependency.getSlot();
                if (slot == null) {
                    slot = "main";
                }

                requiredModules.add(name + ":" + slot);
                getLog().info(" - requires: " + name + ":" + slot);
            }
        } else if (rootName.equals("module-alias")) {
            ModuleAliasDescriptor desc = new ModuleAliasDescriptorImpl(null, node);
            String name = desc.getTargetName();
            String slot = desc.getTargetSlot();
            if (slot == null) {
                slot = "main";
            }
            requiredModules.add(name + ":" + slot);
            getLog().info(" - requires: " + name + ":" + slot);
        } else {

        }
    }

    private final static String MODULES_PREFIX = "modules/";

    private final static String MODULES_LAYERS_PREFIX = MODULES_PREFIX + "system/layers/";

    private final static String MODULES_SUFFIX = "/module.xml";

    private static Pattern ARTIFACT_PATTERN = Pattern.compile("<artifact groupId=\"([^\"]+)\" artifactId=\"([^\"]+)\" version=\"([^\"]+)\"( classifier=\"([^\"]+)\")?.*");

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String projectOutputDir;

    @Inject
    private ArtifactResolver resolver;

    private Map<String, ModuleDescriptor> modules = new HashMap<>();

    private ModuleRewriteConf rules;

    private Set<String> allArtifacts = new HashSet<>();

}
