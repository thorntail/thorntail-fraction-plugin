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
import java.text.DecimalFormatSymbols;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
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
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.utils.FilteringHashSet;
import org.wildfly.swarm.plugin.utils.NamespacePreservingModuleDescriptor;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModuleFiller {

    private final Log log;

    private final Predicate<String> noPlatformModules = module -> !module.startsWith("java.")
            && !module.startsWith("javafx.")
            && !module.startsWith("jdk.")
            && !module.startsWith("org.jboss.modules");

    public ModuleFiller(Log log,
                        DefaultRepositorySystemSession repositorySystemSession,
                        ArtifactResolver resolver,
                        MavenProject project) {
        this.log = log;
        this.repositorySystemSession = repositorySystemSession;
        this.resolver = resolver;
        this.project = project;
    }

    public FractionMetadata apply(FractionMetadata meta) throws MojoExecutionException {
        try {
            this.meta = meta;
            this.rules = new ModuleRewriteConf(this.project);

            Set<String> requiredModules = new HashSet<>();
            Set<String> availableModules = new HashSet<>();
            walkProjectModules(requiredModules, availableModules);
            walkDependencyModules(availableModules);

            Map<String, File> potentialModules = new HashMap<>();
            indexPotentialModules(potentialModules);

            if (requiredModules.isEmpty()) {
                return meta;
            }

            locateFillModules(potentialModules, requiredModules, availableModules);

            DecimalFormat fmt = new DecimalFormat("###0.00", new DecimalFormatSymbols(Locale.US));
            double bytesInMegabyte = 1024.0 * 1024.0;

            long size = 0;
            boolean unknownSize = false;
            for (Artifact artifact : this.allArtifacts) {
                ArtifactRequest req = new ArtifactRequest();
                req.setArtifact(artifact);

                String artifactSizeStr = "???";
                try {
                    ArtifactResult artifactResult = this.resolver.resolveArtifact(repositorySystemSession, req);
                    if (artifactResult.isResolved()) {
                        File file = artifactResult.getArtifact().getFile();
                        long artifactSize = Files.size(file.toPath());
                        size += artifactSize;
                        artifactSizeStr = fmt.format(artifactSize / bytesInMegabyte);
                    } else {
                        unknownSize = true;
                    }
                } catch (ArtifactResolutionException | IOException e) {
                    unknownSize = true;
                }
                this.log.info(String.format("%100s %10s MB", ModuleXmlArtifact.from(artifact), artifactSizeStr));
            }
            String sizeStr;
            if (unknownSize && size == 0) {
                sizeStr = "unknown";
            } else {
                sizeStr = fmt.format(size / bytesInMegabyte) + (unknownSize ? "+" : "") + " MB";
            }

            this.log.info(this.project.getArtifactId() + ": total size:  " + sizeStr);
        } catch (IOException e) {
            String resourceDirs = project.getResources()
                    .stream()
                    .map(Resource::getDirectory)
                    .collect(Collectors.joining(", "));
            throw new MojoExecutionException("Failed collecting module dependencies of " + meta
                    + ", check feature pack ZIP dependencies declared in " + project.getFile()
                    + " and module.xml files declared in " + resourceDirs, e);
        }

        return meta;
    }

    private void locateFillModules(Map<String, File> potentialModules, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoExecutionException {
        while (true) {
            Set<String> fillModules = new FilteringHashSet<>(noPlatformModules);
            fillModules.addAll(requiredModules);
            fillModules.removeAll(availableModules);

            if (fillModules.isEmpty()) {
                break;
            }

            Set<File> featurePackZips = new HashSet<>();
            for (String each : fillModules) {
                File featurePackZip = potentialModules.get(each);
                if (featurePackZip == null) {
                    throw new IOException("Unable to locate feature pack ZIP for module " + each);
                }
                featurePackZips.add(featurePackZip);
            }

            addFillModules(fillModules, featurePackZips, requiredModules, availableModules);
        }
    }

    private void addFillModules(Set<String> fillModules, Set<File> featurePackZips, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoExecutionException {
        for (File featurePackZip : featurePackZips) {
            addFillModules(fillModules, featurePackZip, requiredModules, availableModules);
        }
    }

    private void addFillModules(Set<String> fillModules, File featurePackZip, Set<String> requiredModules, Set<String> availableModules) throws IOException, MojoExecutionException {
        Map<String, ZipEntry> moduleXmls = new HashMap<>();
        ZipEntry featurePackXml = null;

        try (ZipFile zip = new ZipFile(featurePackZip)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                String coreName = null;

                if (name.equals("wildfly-feature-pack.xml")) {
                    featurePackXml = entry;
                } else if (name.startsWith(MODULES_SYSTEM_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_SYSTEM_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                    coreName = coreName.substring(coreName.indexOf('/') + 1);
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
                throw new MojoExecutionException("Unable to find wildfly-feature-pack.xml in " + featurePackZip);
            }

            Map<String, Artifact> artifacts = processFeaturePackXml(zip.getInputStream(featurePackXml));

            for (String moduleName : moduleXmls.keySet()) {
                ZipEntry entry = moduleXmls.get(moduleName);
                addFillModule(artifacts, moduleName, zip.getInputStream(entry), requiredModules, availableModules);
                addResources(zip, moduleName, entry);
            }
        }
    }

    private void addResources(ZipFile zip, String moduleName, ZipEntry moduleXml) {

        String moduleXmlPath = moduleXml.getName();
        String rootName = moduleXmlPath.substring(0, moduleXmlPath.length() - MODULE_XML.length());

        Enumeration<? extends ZipEntry> entries = zip.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                if (entry.getName().startsWith(rootName) && !entry.getName().equals(moduleXmlPath)) {

                    String resourceRelative = entry.getName().substring(rootName.length());
                    resourceRelative = resourceRelative.replace('/', File.separatorChar);
                    Path classesDir = Paths.get(this.project.getBuild().getOutputDirectory());
                    Path modulesDir = classesDir.resolve(MODULES);

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

    private void addFillModule(Map<String, Artifact> artifacts, String moduleName, InputStream in, Set<String> requiredModules, Set<String> availableModules) throws IOException {

        Path classesDir = Paths.get(this.project.getBuild().getOutputDirectory());
        Path modulesDir = classesDir.resolve(MODULES);

        String[] parts = moduleName.split(":");
        String[] moduleParts = parts[0].split("\\.");

        Path moduleDir = modulesDir;

        for (int i = 0; i < moduleParts.length; ++i) {
            moduleDir = moduleDir.resolve(moduleParts[i]);
        }

        moduleDir = moduleDir.resolve(parts[1]);

        Path moduleXml = moduleDir.resolve(MODULE_XML);

        processFillModule(artifacts, moduleXml, in);

        analyzeModuleXml(modulesDir, moduleXml, requiredModules, availableModules);
    }

    private void processFillModule(Map<String, Artifact> artifacts, Path moduleXml, InputStream in) throws IOException {
        Files.createDirectories(moduleXml.getParent());

        NodeImporter importer = new XmlDomNodeImporterImpl();
        Node node = importer.importAsNode(in, true);

        String rootName = node.getName();

        if (rootName.equals("module")) {
            ModuleDescriptor desc = new NamespacePreservingModuleDescriptor(null, node);
            for (ArtifactType<ResourcesType<ModuleDescriptor>> moduleArtifact : desc.getOrCreateResources().getAllArtifact()) {
                String name = moduleArtifact.getName();
                if (name.startsWith("${")) {
                    name = name.substring(2, name.length() - 1);
                }
                if (name.endsWith("?jandex")) {
                    name = name.replace("?jandex", "");
                }

                Artifact artifact = artifacts.get(name);
                if (artifact == null) {
                    String[] parts = name.split(":");

                    if (parts.length == 3) {
                        artifacts.put(name, artifact = new DefaultArtifact(parts[0], parts[1], "jar", parts[2]));
                    } else if (parts.length == 5) {
                        artifacts.put(name, artifact = new DefaultArtifact(parts[0], parts[1], parts[3], parts[2], parts[4]));
                    } else {
                        throw new RuntimeException("Could not resolve module artifact " + name);
                    }
                }
                moduleArtifact.name(ModuleXmlArtifact.from(artifact).toString());

                this.allArtifacts.add(artifact);
            }

            ((NamespacePreservingModuleDescriptor) desc).fillVersionAttribute(artifacts);

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

    private Map<String, Artifact> processFeaturePackXml(InputStream in) throws IOException {
        final Map<String, Artifact> artifacts = new HashMap<>();
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

                    artifacts.put(expr, new DefaultArtifact(groupId, artifactId, classifier, "jar", version));
                }
            }
        }
        return artifacts;
    }

    private void indexPotentialModules(Map<String, File> potentialModules) throws IOException {
        Set<org.apache.maven.artifact.Artifact> artifacts = this.project.getArtifacts();

        for (org.apache.maven.artifact.Artifact each : artifacts) {
            if (each.getType().equals("zip")) {
                indexPotentialModules(each.getFile(), potentialModules);
            }
        }
    }

    private void indexPotentialModules(File file, Map<String, File> potentialModules) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry each = entries.nextElement();
                String name = each.getName();
                String coreName = null;
                if (name.startsWith(MODULES_SYSTEM_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_SYSTEM_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                    coreName = coreName.substring(coreName.indexOf('/') + 1);
                    coreName = coreName.substring(coreName.indexOf('/') + 1);
                } else if (name.startsWith(MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    coreName = name.substring(MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());
                }

                if (coreName != null) {
                    int lastSlashLoc = coreName.lastIndexOf('/');

                    String moduleName = coreName.substring(0, lastSlashLoc);
                    String slot = coreName.substring(lastSlashLoc + 1);

                    moduleName = moduleName.replace('/', '.');

                    String key = moduleName + ":" + slot;
                    if (potentialModules.containsKey(key)) {
                        log.warn("Duplicate module '" + key + "': previously found in "
                                + potentialModules.get(key) + ", but " + file + " will be used");
                    }
                    potentialModules.put(key, file);
                }
            }
        }
    }

    private void walkProjectModules(final Set<String> requiredModules, final Set<String> availableModules) throws IOException {
        List<Resource> resources = this.project.getBuild().getResources();
        for (Resource each : resources) {
            final Path modulesDir = Paths.get(each.getDirectory()).resolve(MODULES);
            if (Files.exists(modulesDir)) {
                Files.walkFileTree(modulesDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.getFileName().toString().equals(MODULE_XML)) {
                            analyzeModuleXml(modulesDir, file, requiredModules, availableModules);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }

        Path targetModulesDir = Paths.get(this.project.getBuild().getOutputDirectory()).resolve(MODULES);

        if (Files.exists(targetModulesDir)) {
            Files.walkFileTree(targetModulesDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals(MODULE_XML)) {
                        analyzeModuleXml(targetModulesDir, file, requiredModules, availableModules);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void walkDependencyModules(Set<String> availableModules) throws IOException {
        Set<org.apache.maven.artifact.Artifact> artifacts = this.project.getArtifacts();

        for (org.apache.maven.artifact.Artifact each : artifacts) {
            collectAvailableModules(each, availableModules);
        }
    }

    private void collectAvailableModules(org.apache.maven.artifact.Artifact artifact, Set<String> modules) throws IOException {
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

    private void analyzeModuleXml(Path root, Path moduleXml, Set<String> requiredModules, Set<String> availableModules) throws IOException {

        ModuleAnalyzer analyzer = new ModuleAnalyzer(moduleXml);
        this.meta.addTransitiveDependencies(analyzer.getDependencies());
        Path modulePath = root.relativize(moduleXml).getParent();


        String selfSlot = modulePath.getName(modulePath.getNameCount() - 1).toString();
        String selfModuleName = modulePath.getParent().toString().replace(File.separatorChar, '.');

        this.log.info("Analyzing: " + selfModuleName + ":" + selfSlot + " (" + moduleXml + ")");

        availableModules.add(selfModuleName + ":" + selfSlot);

        NodeImporter importer = new XmlDomNodeImporterImpl();
        Node node = importer.importAsNode(new FileInputStream(moduleXml.toFile()), true);

        String rootName = node.getName();

        switch (rootName) {
            case "module": {
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
                    this.log.info(" - requires: " + name + ":" + slot);
                }
                break;
            }
            case "module-alias": {
                ModuleAliasDescriptor desc = new ModuleAliasDescriptorImpl(null, node);
                String name = desc.getTargetName();
                String slot = desc.getTargetSlot();
                if (slot == null) {
                    slot = "main";
                }
                requiredModules.add(name + ":" + slot);
                this.log.info(" - requires: " + name + ":" + slot);
                break;
            }
            default:
        }
    }

    private static final String MODULES = "modules";

    private static final String MODULES_PREFIX = MODULES + "/";

    private static final String MODULES_SYSTEM_PREFIX = MODULES_PREFIX + "system/";

    private static final String MODULE_XML = "module.xml";

    private static final String MODULES_SUFFIX = "/" + MODULE_XML;

    private static Pattern ARTIFACT_PATTERN = Pattern.compile("<artifact groupId=\"([^\"]+)\" artifactId=\"([^\"]+)\" version=\"([^\"]+)\"( classifier=\"([^\"]+)\")?.*");

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Inject
    private ArtifactResolver resolver;

    private ModuleRewriteConf rules;

    private Set<Artifact> allArtifacts = new HashSet<>();

    private FractionMetadata meta;
}
