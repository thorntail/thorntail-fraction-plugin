package org.wildfly.swarm.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.ArtifactType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.DependenciesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.FilterType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.ModuleDependencyType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.ModuleDescriptor;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.PathSetType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.ResourcesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule15.SystemDependencyType;

/**
 * @author Bob McWhirter
 */
public class ModuleGenerator {

    private final Log log;

    private final MavenProject project;

    public ModuleGenerator(Log log,
                           MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public void execute() throws IOException {
        Path moduleConf = this.project.getBasedir().toPath().resolve("module.conf");

        System.err.println("test " + moduleConf);
        if (!Files.exists(moduleConf)) {
            return;
        }

        BufferedReader reader = new BufferedReader(new FileReader(moduleConf.toFile()));
        List<String> dependencies = reader.lines().collect(Collectors.toList());

        Path moduleRoot = determineModuleRoot();

        generate(moduleRoot, dependencies);
    }

    public void generate(Path root, List<String> dependencies) throws IOException {

        String moduleName = root.toString().replace(File.separatorChar, '.');

        Path outputDir = Paths.get(this.project.getBuild().getOutputDirectory(), "modules");

        Path runtimeModuleXml = outputDir.resolve(root).resolve(Paths.get("runtime", "module.xml"));
        Path apiModuleXml = outputDir.resolve(root).resolve(Paths.get("api", "module.xml"));
        Path mainModuleXml = outputDir.resolve(root).resolve(Paths.get("main", "module.xml"));

        Set<String> apiPaths = determineApiPaths();

        // -- runtime

        ModuleDescriptor runtimeModule = Descriptors.create(ModuleDescriptor.class);
        runtimeModule
                .name(moduleName)
                .slot("runtime");

        ArtifactType<ResourcesType<ModuleDescriptor>> runtimeArtifact = runtimeModule.getOrCreateResources().createArtifact();
        runtimeArtifact.name(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion());

        PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> excludeSet = runtimeArtifact.getOrCreateFilter()
                .createExcludeSet();

        for (String path : apiPaths) {
            excludeSet.createPath().name(path);
        }

        runtimeModule.getOrCreateDependencies()
                .createModule().name(moduleName).slot("main").up()
                .createModule().name("org.wildfly.swarm.bootstrap").optional(true).up()
                .createModule().name("org.wildfly.swarm.container").slot("runtime").up();


        addDependencies( runtimeModule, dependencies );

        // -- api

        ModuleDescriptor apiModule = Descriptors.create(ModuleDescriptor.class);
        apiModule.name(moduleName).slot("api");

        ArtifactType<ResourcesType<ModuleDescriptor>> apiArtifact = apiModule.getOrCreateResources().createArtifact();
        apiArtifact.name(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion());

        PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> includeSet = apiArtifact.getOrCreateFilter()
                .createIncludeSet();

        for (String path : apiPaths) {
            includeSet.createPath().name(path);
        }

        apiModule.getOrCreateDependencies()
                .createModule()
                .name("org.wildfly.swarm.container");

        addDependencies( apiModule, dependencies );

        // -- main

        ModuleDescriptor mainModule = Descriptors.create(ModuleDescriptor.class);
        mainModule.name(moduleName).slot("main");

        SystemDependencyType<DependenciesType<ModuleDescriptor>> system = mainModule.getOrCreateDependencies().createSystem();

        system.export(true);
        PathSetType<SystemDependencyType<DependenciesType<ModuleDescriptor>>> systemPaths = system.getOrCreatePaths();

        for (String path : apiPaths) {
            systemPaths.createPath().name(path);
        }

        mainModule.getOrCreateDependencies()
                .createModule()
                .name(moduleName)
                .slot(apiModule.getSlot())
                .export(true)
                .services("export");


        export(mainModule, mainModuleXml);
        export(apiModule, apiModuleXml);
        export(runtimeModule, runtimeModuleXml);
        //System.err.println(mainModule.exportAsString());
        //System.err.println(apiModule.exportAsString());
        //System.err.println(runtimeModule.exportAsString());
    }

    private void addDependencies(ModuleDescriptor module, List<String> dependencies) {

        for (String dependency : dependencies) {
            dependency = dependency.trim();
            if (!dependency.isEmpty()) {
                String services = null;
                if (dependency.contains("services=export")) {
                    services = "export";
                    dependency = dependency.replace("services=export", "");
                } else if (dependency.contains("services=import")) {
                    services = "import";
                    dependency = dependency.replace("services=import", "");
                }
                dependency = dependency.trim();
                int colonLoc = dependency.indexOf(':');

                String depName;
                String depSlot;

                if (colonLoc < 0) {
                    depName = dependency;
                    depSlot = "main";
                } else {
                    depName = dependency.substring(0, colonLoc);
                    depSlot = dependency.substring(colonLoc + 1);
                }
                ModuleDependencyType<DependenciesType<ModuleDescriptor>> moduleDep = module.getOrCreateDependencies()
                        .createModule()
                        .name( depName )
                        .slot( depSlot );

                if (services != null) {
                    moduleDep.services(services);
                }
            }
        }

    }

    private void export(ModuleDescriptor module, Path path) throws IOException {
        System.err.println("export to : " + path);
        System.err.println(module.exportAsString());
        Files.createDirectories(path.getParent());
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            module.exportTo(out);
        }

    }

    public Path determineModuleRoot() throws IOException {
        Path target = Paths.get(this.project.getBuild().getOutputDirectory());

        AtomicReference<Path> root = new AtomicReference<>();

        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith("Fraction.class")) {
                    Path path = target.relativize(file.getParent());
                    root.set(path);
                }
                return super.visitFile(file, attrs);
            }
        });

        return root.get();
    }

    public Set<String> determineApiPaths() throws IOException {
        Path dir = Paths.get(this.project.getBuild().getOutputDirectory());

        Set<String> apiPaths = new HashSet<>();

        FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".class")) {
                    if (file.toString().contains("runtime")) {
                        // ignore
                    } else {
                        apiPaths.add(dir.relativize(file.getParent()).toString());
                    }
                }
                return super.visitFile(file, attrs);
            }
        };

        Files.walkFileTree(dir, visitor);
        return apiPaths;

    }


}
