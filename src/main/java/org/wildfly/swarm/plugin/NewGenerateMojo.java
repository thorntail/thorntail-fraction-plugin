package org.wildfly.swarm.plugin;

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
import org.eclipse.aether.impl.ArtifactResolver;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
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
public class NewGenerateMojo extends AbstractMojo {

    private final static String MODULES_PREFIX = "modules/";
    private final static String LAYERED_MODULES_PREFIX = MODULES_PREFIX + "system/layers/base/";
    private final static String MODULES_SUFFIX = "/module.xml";

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String projectOutputDir;

    @Parameter(alias = "feature-pack")
    private String featurePack;

    @Inject
    private ArtifactResolver resolver;

    private static final String PREFIX = "wildfly-swarm-";

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Set<String> requiredModules = new HashSet<>();
            Set<String> availableModules = new HashSet<>();
            walkProjectModules(requiredModules, availableModules);
            walkDependencyModules(requiredModules, availableModules);

            Map<String, File> potentialModules = new HashMap<>();
            indexPotentialModules(potentialModules);

            locateFillModules(potentialModules, requiredModules, availableModules);
        } catch (IOException e) {
            throw new MojoFailureException("Unable to walk modules directory");
        }
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

                if (name.equals("wildfly-feature-pack.xml")) {
                    featurePackXml = entry;
                } else if (name.startsWith(LAYERED_MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    String coreName = name.substring(LAYERED_MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in));
             Writer out = new FileWriter(moduleXml.toFile())) {

            String line = null;

            while ((line = reader.readLine()) != null) {
                out.write(processLine(versions, line) + "\n");
            }
        }
    }

    private static final Pattern EXPR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    protected String processLine(Map<String, String> versions, String line) {

        Matcher matcher = EXPR_PATTERN.matcher(line);

        if (matcher.find()) {
            String match = matcher.group(0);
            String expr = matcher.group(1);

            if ( expr.endsWith( "?jandex" ) ) {
                expr = expr.replace( "?jandex", "" );
            }

            String replacement = versions.get(expr);

            return matcher.replaceFirst(replacement);

        } else {
            return line;
        }
    }


    private static Pattern ARTIFACT_PATTERN = Pattern.compile("<artifact groupId=\"([^\"]+)\" artifactId=\"([^\"]+)\" version=\"([^\"]+)\"( classifier=\"([^\"]+)\")?.*");

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
                if (name.startsWith(LAYERED_MODULES_PREFIX) && name.endsWith(MODULES_SUFFIX)) {
                    String coreName = name.substring(LAYERED_MODULES_PREFIX.length(), name.length() - MODULES_SUFFIX.length());

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

    private static Pattern MODULE_PATTERN = Pattern.compile("<module name=\"([^\"]+)\"( slot=\"([^\"]+)\")?.*");

    protected void analyzeModuleXml(Path root, Path moduleXml, Set<String> requiredModules, Set<String> availableModules) throws IOException {
        Path modulePath = root.relativize(moduleXml).getParent();

        String selfSlot = modulePath.getName(modulePath.getNameCount() - 1).toString();
        String selfModuleName = modulePath.getParent().toString().replace(File.separatorChar, '.');

        availableModules.add(selfModuleName + ":" + selfSlot);
        try (BufferedReader reader = new BufferedReader(new FileReader(moduleXml.toFile()))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                Matcher matcher = MODULE_PATTERN.matcher(line);

                if (matcher.matches()) {
                    if ( line.contains( "optional" ) ) {
                        continue;
                    }
                    MatchResult result = matcher.toMatchResult();

                    String moduleName = result.group(1);
                    String slot = result.group(3);
                    if (slot == null) {
                        slot = "main";
                    }
                    requiredModules.add(moduleName + ":" + slot);
                }
            }
        }
    }

}
