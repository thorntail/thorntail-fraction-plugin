package org.wildfly.swarm.plugin.process;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
import org.wildfly.swarm.plugin.FractionMetadata;

/**
 * @author Bob McWhirter
 */
public class ModuleGenerator implements Function<FractionMetadata, FractionMetadata> {

    private static final String RUNTIME = "runtime";

    private static final String MAIN = "main";

    private static final String DEPLOYMENT = "deployment";

    private static final String DETECT = "detect";

    private static final String MODULE_XML = "module.xml";

    private final Log log;

    private final MavenProject project;

    public ModuleGenerator(Log log,
                           MavenProject project) {
        this.log = log;
        this.project = project;
    }

    @Override
    public FractionMetadata apply(FractionMetadata meta) {
        if (meta.hasModuleConf()) {
            Path moduleConf = meta.getModuleConf();
            List<String> dependencies;
            try (BufferedReader reader = new BufferedReader(new FileReader(moduleConf.toFile()))) {
                dependencies = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .collect(Collectors.toList());
                generate(meta.getBaseModulePath(), dependencies);
            } catch (IOException e) {
                this.log.error(e.getMessage(), e);
            }
        }
        return meta;
    }

    private void generate(Path root, List<String> dependencies) throws IOException {

        String moduleName = root.toString().replace(File.separatorChar, '.');

        Path outputDir = Paths.get(this.project.getBuild().getOutputDirectory(), "modules");

        Path runtimeModuleXml = outputDir.resolve(root).resolve(Paths.get(RUNTIME, MODULE_XML));
        Path apiModuleXml = outputDir.resolve(root).resolve(Paths.get("api", MODULE_XML));
        Path mainModuleXml = outputDir.resolve(root).resolve(Paths.get(MAIN, MODULE_XML));
        Path deploymentModuleXml = outputDir.resolve(root).resolve(Paths.get(DEPLOYMENT, MODULE_XML));

        Set<String> apiPaths = determineApiPaths();
        Set<String> runtimePaths = determineRuntimePaths();
        Set<String> deploymentPaths = determineDeploymentPaths();

        // -- runtime
        ModuleDescriptor runtimeModule = Descriptors.create(ModuleDescriptor.class);
        runtimeModule
                .name(moduleName)
                .slot(RUNTIME);

        ArtifactType<ResourcesType<ModuleDescriptor>> runtimeArtifact = runtimeModule.getOrCreateResources().createArtifact();
        runtimeArtifact.name(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion());

        PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> runtimeExcludeSet = runtimeArtifact.getOrCreateFilter()
                .createExcludeSet();

        for (String path : apiPaths) {
            runtimeExcludeSet.createPath().name(path);
        }

        for (String path : deploymentPaths) {
            runtimeExcludeSet.createPath().name(path);
        }

        runtimeModule.getOrCreateDependencies()
                .createModule().name(moduleName).slot(MAIN).export(true);

        runtimeModule.getOrCreateDependencies()
                .createModule().name("org.wildfly.swarm.bootstrap").optional(true).up()
                .createModule().name("org.wildfly.swarm.container").slot(RUNTIME).up()
                .createModule().name("org.wildfly.swarm.spi").slot(RUNTIME).up();


        runtimeModule.getOrCreateDependencies()
                .createModule()
                .name("javax.enterprise.api");

        runtimeModule.getOrCreateDependencies()
                .createModule()
                .name("org.jboss.weld.se");

        addDependencies(runtimeModule, dependencies);

        // -- api

        ModuleDescriptor apiModule = Descriptors.create(ModuleDescriptor.class);
        apiModule.name(moduleName).slot("api");

        ArtifactType<ResourcesType<ModuleDescriptor>> apiArtifact = apiModule.getOrCreateResources().createArtifact();
        apiArtifact.name(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion());

        PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> apiIncludeSet = apiArtifact.getOrCreateFilter()
                .createIncludeSet();

        for (String path : apiPaths) {
            apiIncludeSet.createPath().name(path);
        }

        apiIncludeSet.createPath().name("META-INF");

        PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> apiExcludeSet = apiArtifact.getOrCreateFilter()
                .createExcludeSet();

        for (String path : runtimePaths) {
            apiExcludeSet.createPath().name(path);
        }

        for (String path : deploymentPaths) {
            apiExcludeSet.createPath().name(path);
        }

        apiModule.getOrCreateDependencies()
                .createModule()
                .name("org.wildfly.swarm.container");


        apiModule.getOrCreateDependencies()
                .createModule()
                .name("javax.enterprise.api");

        apiModule.getOrCreateDependencies()
                .createModule()
                .name("org.jboss.weld.se");

        addDependencies(apiModule, dependencies);

        // -- main

        ModuleDescriptor mainModule = Descriptors.create(ModuleDescriptor.class);
        mainModule.name(moduleName).slot(MAIN);

        SystemDependencyType<DependenciesType<ModuleDescriptor>> system = mainModule.getOrCreateDependencies().createSystem();

        system.export(true);
        PathSetType<SystemDependencyType<DependenciesType<ModuleDescriptor>>> systemPaths = system.getOrCreatePaths();

        for (String path : apiPaths) {
            systemPaths.createPath().name(path);
        }

        ModuleDependencyType<DependenciesType<ModuleDescriptor>> depModule = mainModule.getOrCreateDependencies()
                .createModule();
        depModule.name(moduleName)
                .slot(apiModule.getSlot())
                .export(true)
                .services("export");

        FilterType<ModuleDependencyType<DependenciesType<ModuleDescriptor>>> imports = depModule.getOrCreateImports();

        for (String path : apiPaths) {
            imports.createInclude().path(path);
        }

        imports.getOrCreateInclude().path("**");

        FilterType<ModuleDependencyType<DependenciesType<ModuleDescriptor>>> exports = depModule.getOrCreateExports();

        exports.createInclude().path("**");

        // -- deployment

        ModuleDescriptor deploymentModule = null;

        if (!deploymentPaths.isEmpty()) {
            deploymentModule = Descriptors.create(ModuleDescriptor.class);
            deploymentModule
                    .name(moduleName)
                    .slot(DEPLOYMENT);

            ArtifactType<ResourcesType<ModuleDescriptor>> deploymentArtifact = deploymentModule.getOrCreateResources().createArtifact();
            deploymentArtifact.name(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion());

            PathSetType<FilterType<ArtifactType<ResourcesType<ModuleDescriptor>>>> deploymentExcludeSet = deploymentArtifact.getOrCreateFilter()
                    .createExcludeSet();

            for (String path : apiPaths) {
                deploymentExcludeSet.createPath().name(path);
            }

            for (String path : runtimePaths) {
                deploymentExcludeSet.createPath().name(path);
            }

            ModuleDependencyType<DependenciesType<ModuleDescriptor>> deploymentMainDep = deploymentModule.getOrCreateDependencies()
                    .createModule();
            deploymentMainDep.name(moduleName)
                    .slot(mainModule.getSlot());

            addDependencies(deploymentModule, dependencies);


        }

        export(mainModule, mainModuleXml);
        export(apiModule, apiModuleXml);
        export(runtimeModule, runtimeModuleXml);
        export(deploymentModule, deploymentModuleXml);
    }

    private void addDependencies(ModuleDescriptor module, List<String> dependencies) {

        for (String dependency : dependencies) {
            dependency = dependency.trim();
            if (!dependency.isEmpty()) {
                boolean optional = false;
                if (dependency.startsWith("*")) {
                    optional = true;
                    dependency = dependency.substring(1);
                }
                String services = null;
                if (dependency.contains("services=export")) {
                    services = "export";
                    dependency = dependency.replace("services=export", "");
                } else if (dependency.contains("services=import")) {
                    services = "import";
                    dependency = dependency.replace("services=import", "");
                }
                boolean isexport = false;
                if (dependency.contains("export=true")) {
                    isexport = true;
                    dependency = dependency.replace("export=true", "");
                }
                dependency = dependency.trim();
                int colonLoc = dependency.indexOf(':');

                String depName;
                String depSlot;

                if (colonLoc < 0) {
                    depName = dependency;
                    depSlot = MAIN;
                } else {
                    depName = dependency.substring(0, colonLoc);
                    depSlot = dependency.substring(colonLoc + 1);
                }
                ModuleDependencyType<DependenciesType<ModuleDescriptor>> moduleDep = module.getOrCreateDependencies()
                        .createModule()
                        .name(depName)
                        .slot(depSlot);

                if (services != null) {
                    moduleDep.services(services);
                }
                if (isexport) {
                    moduleDep.export(isexport);
                }
                if (optional) {
                    moduleDep.optional(true);
                }
            }
        }

    }

    private void export(ModuleDescriptor module, Path path) throws IOException {
        if (module == null) {
            log.info("Not exporting empty module: " + path);
            return;
        }
        Files.createDirectories(path.getParent());
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            module.exportTo(out);
        }
    }

    private Set<String> determineApiPaths() throws IOException {
        return determinePaths((file) -> (!(file.contains(RUNTIME) || file.contains(DEPLOYMENT) || file.contains(DETECT))));
    }

    private Set<String> determineRuntimePaths() throws IOException {
        return determinePaths((file) -> file.contains(RUNTIME));
    }

    private Set<String> determineDeploymentPaths() throws IOException {
        return determinePaths((file) -> file.contains(DEPLOYMENT));
    }

    private Set<String> determinePaths(Predicate<String> pred) throws IOException {
        Path dir = Paths.get(this.project.getBuild().getOutputDirectory());

        Set<String> paths = new HashSet<>();

        if (Files.exists(dir)) {
            FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".class")) {
                        if (pred.test(file.toString())) {
                            paths.add(javaSlashize(dir.relativize(file.getParent())));
                        }
                    }
                    return super.visitFile(file, attrs);
                }
            };

            Files.walkFileTree(dir, visitor);
        }
        return paths;

    }

    private String javaSlashize(Path path) {
        List<String> parts = new ArrayList<>();

        int numParts = path.getNameCount();

        for (int i = 0; i < numParts; ++i) {
            parts.add(path.getName(i).toString());
        }

        return String.join("/", parts);
    }

}
