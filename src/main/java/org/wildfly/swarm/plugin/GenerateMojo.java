package org.wildfly.swarm.plugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
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

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class GenerateMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String projectOutputDir;

    @Parameter(readonly = true, defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    @Parameter(alias = "modules")
    private String[] modules;

    @Parameter(alias = "exports")
    private String[] exports;

    @Parameter(alias="feature-pack")
    private String featurePack;

    @Parameter(alias="module-name", defaultValue = "${fraction-module}")
    private String fractionModuleName;

    @Inject
    private ArtifactResolver resolver;

    private String className;
    private String packageName;

    private static final String PREFIX = "wildfly-swarm-";

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.modules == null || this.modules.length == 0) {
            throw new MojoFailureException("At least 1 module needs to be configured");
        }

        // Set the charset for writing files, default to UTF-8
        final Charset charset = (encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding));

        determineClassName();

        if (fractionModuleName == null || fractionModuleName.length() == 0) {
            throw new MojoFailureException("This plugin requires the 'fraction-module' property to be set.");
        }

        generateServiceLoaderDescriptor(charset);
        generateFeaturePack(charset);
        generateFractionReferenceForJar(charset);
        generateFeaturePackReferenceForJar(charset);
    }

    private void generateFractionReferenceForJar(final Charset charset) throws MojoFailureException {
        final Path reference = Paths.get(this.projectOutputDir, "wildfly-swarm-fraction.gav");
        try (final BufferedWriter out = Files.newBufferedWriter(reference, charset)) {
            out.write(project.getGroupId());
            out.write(':');
            out.write(project.getArtifactId());
            out.write(":zip:fraction:");
            out.write(project.getVersion());
            out.write('\n');
        } catch (IOException e) {
            throw new MojoFailureException("unable to create fraction reference for jar", e);
        }
    }

    private void generateFeaturePackReferenceForJar(final Charset charset) throws MojoFailureException {
        if ( this.featurePack == null ) {
            return;
        }
        String[] parts = this.featurePack.split(":");
        // TODO (jrp) using the dependency management doesn't seem correct, it should likely use the explicit dependencies
        List<Dependency> deps = this.project.getDependencyManagement().getDependencies();

        Dependency featurePackDep = null;
        for (Dependency each : deps) {
            if (each.getGroupId().equals(parts[0]) && each.getArtifactId().equals(parts[1]) && each.getType().equals("zip")) {
                getLog().info("Using feature-pack: " + each);
                featurePackDep = each;
                break;
            }
        }

        if (featurePackDep == null) {
            throw new MojoFailureException("Unable to determine feature-pack: " + featurePack);
        }

        final Path reference = Paths.get(this.projectOutputDir, "wildfly-swarm-feature-pack.gav");

        try (final BufferedWriter out = Files.newBufferedWriter(reference, charset)){
            out.write(featurePackDep.getGroupId());
            out.write(':');
            out.write(featurePackDep.getArtifactId());
            out.write(":zip:");
            out.write(featurePackDep.getVersion());
            out.write('\n');
        } catch (IOException e) {
            throw new MojoFailureException("unable to create feature-pack reference for jar", e);
        }
    }


    private void generateFeaturePack(final Charset charset) throws MojoFailureException {
        generateModule(charset);
        createZip();
    }


    private void createZip() throws MojoFailureException {
        final Path zipFile = Paths.get(this.projectBuildDir, this.project.getArtifactId() + "-" + this.project.getVersion() + "-fraction.zip");

        try {
            Files.createDirectories(zipFile.getParent());
            final Path dirTarget = Paths.get(this.projectBuildDir, "fraction");
            compress(dirTarget, zipFile);
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create fraction.zip file", e);

        }

        org.apache.maven.artifact.DefaultArtifact zipArtifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "provided",
                "zip",
                "fraction",
                new DefaultArtifactHandler("zip")
        );

        zipArtifact.setFile(zipFile.toFile());

        this.project.addAttachedArtifact(zipArtifact);
    }

    private void generateModule(final Charset charset) throws MojoFailureException {
        final Path dir = Paths.get(this.projectBuildDir, "fraction", "modules", "system", "layers", "base",
                project.getGroupId().replace('.', File.separatorChar), fractionModuleName, "main");

        final Path moduleXml = dir.resolve("module.xml");


        try {
            Files.createDirectories(dir);
            try (final BufferedWriter out = Files.newBufferedWriter(moduleXml, charset)){
                // Main module element
                out.write("<module xmlns=\"urn:jboss:module:1.3\" name=\"");
                out.write(project.getGroupId());
                out.write('.');
                out.write(fractionModuleName);
                out.write("\">\n");

                // Write the resources
                out.write("  <resources>\n");
                out.write("    <artifact name=\"${" + this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion() + "}\"/>\n");
                out.write("  </resources>\n");

                // Write the dependencies
                out.write("  <dependencies>\n");
                out.write("    <module name=\"org.wildfly.swarm.container\"/>\n");

                for (final String module : modules) {
                    out.write("    <module name=\"");
                    out.write(module.trim());
                    out.write("\"/>\n");
                }

                if (this.exports != null) {
                    for (final String export : this.exports) {
                        out.write("    <module name=\"");
                        out.write(export.trim());
                        out.write("\" export=\"true\"/>\n");
                    }
                }
                out.write("  </dependencies>\n");
                out.write("</module>\n");

            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create module.xml", e);
        }
    }

    private void generateServiceLoaderDescriptor(final Charset charset) throws MojoFailureException {
        if ( this.className == null ) {
            return;
        }
        
        final Path dir = Paths.get(this.projectBuildDir, "classes/META-INF/services");
        final Path services = dir.resolve("org.wildfly.swarm.container.FractionDefaulter");

        try {
            Files.createDirectories(dir);
            try (final BufferedWriter out = Files.newBufferedWriter(services, charset)) {
                out.write(packageName);
                if (packageName.charAt(packageName.length() - 1) != '.') {
                    out.write('.');
                }
                out.write(className);
                out.write('\n');
            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create services file: " + services, e);
        }
    }

    private void determineClassName() throws MojoFailureException {
        try {
            final Path classesDir = Paths.get(projectOutputDir);
            Files.walkFileTree(classesDir, new SimpleFileVisitor<Path>() {
                Path packageDir = Paths.get(".");
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().equals("META-INF")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // Ignore the first directory
                    if (!classesDir.getFileName().equals(dir.getFileName())) {
                        packageDir = packageDir.resolve(dir.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith("FractionDefaulter.class")) {
                        // Strip out .class from name
                        String name = file.getFileName().toString();
                        setClassName(name.substring(0, name.length() - 6));
                        setPackage(packageDir.normalize().toString().replace(File.separatorChar, '.'));
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoFailureException("Unable to determine FractionDefaulter class", e);
        }

        //if (this.className == null || this.packageNameWithTrailingDot == null) {
            //throw new MojoFailureException("Unable to determine FractionDefaulter class");
        //}
    }

    private void setClassName(String className) {
        this.className = className;
        String artifactId = this.project.getArtifactId();
        if (this.fractionModuleName == null && artifactId.startsWith(PREFIX)) {
            this.fractionModuleName = artifactId.substring(PREFIX.length());
        }
    }

    private void setPackage(String name) {
        String packageName = name;
        if (packageName.charAt(0) == '.') {
            packageName = packageName.substring(1);
        }
        this.packageName = packageName;
    }

    private static void compress(final Path in, final Path target) throws IOException {
        // Environment for Zip FileSystem
        final Map<String, String> env = Collections.singletonMap("create", "true");
        // Creating the URI with a jar: prefix creates a zip file system
        final URI targetUri = URI.create("jar:" + target.toUri());
        try (final FileSystem zipFs = FileSystems.newFileSystem(targetUri, env)) {
            // Walk the directory
            Files.walkFileTree(in, new SimpleFileVisitor<Path>() {
                Path currentZipDir = zipFs.getPath(zipFs.getSeparator());
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    // Ignore the same base directory, e.g. fractions/modules should result in /modules
                    if (!dir.getFileName().toString().equals(in.getFileName().toString())) {
                        currentZipDir = currentZipDir.resolve(dir.getFileName().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final Path zipTarget = currentZipDir.resolve(file.getFileName().toString());
                    Files.createDirectories(zipTarget);
                    Files.copy(file, zipTarget, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
