package org.wildfly.swarm.plugin;

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
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(
        name = "xgenerate",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
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

        determineClassName();

        if (fractionModuleName == null || fractionModuleName.length() == 0) {
            throw new MojoFailureException("This plugin requires the 'fraction-module' property to be set.");
        }

        generateServiceLoaderDescriptor();
        generateModule();
    }


    private void generateModule() throws MojoFailureException {
        final Path dir = Paths.get(
                this.projectOutputDir,
                "modules",
                project.getGroupId().replace('.', File.separatorChar), fractionModuleName, "main");

        final Path moduleXml = dir.resolve("module.xml");

        try {
            Files.createDirectories(dir);
            try (final BufferedWriter out = Files.newBufferedWriter(moduleXml, StandardCharsets.UTF_8)){
                // Main module element
                out.write("<module xmlns=\"urn:jboss:module:1.3\" name=\"");
                out.write(project.getGroupId());
                out.write('.');
                out.write(fractionModuleName);
                out.write("\">\n");

                // Write the resources
                out.write("  <resources>\n");
                out.write("    <artifact name=\"" + this.project.getGroupId() + ":" + this.project.getArtifactId() + ":" + this.project.getVersion() + "\"/>\n");
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

    private void generateServiceLoaderDescriptor() throws MojoFailureException {
        if ( this.className == null ) {
            return;
        }

        final Path dir = Paths.get(this.projectBuildDir, "classes/META-INF/services");
        final Path services = dir.resolve("org.wildfly.swarm.container.FractionDefaulter");

        try {
            Files.createDirectories(dir);
            try (final BufferedWriter out = Files.newBufferedWriter(services, StandardCharsets.UTF_8)) {
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
            System.err.println( "classesDir: " + classesDir );
            Files.walkFileTree(classesDir, new SimpleFileVisitor<Path>() {
                Path packageDir = Paths.get(".");
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    System.err.println( "preVisit: " + dir );
                    if (dir.getFileName().toString().equals("META-INF")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // Ignore the first directory
                    if (!classesDir.getFileName().equals(dir.getFileName())) {
                        System.err.println( "pre-resolve: "+  packageDir );
                        packageDir = packageDir.resolve(dir.getFileName());
                        System.err.println( "post-resolve: "+  packageDir );
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    System.err.println( "visitFile: " + file );
                    if (file.getFileName().toString().endsWith("FractionDefaulter.class")) {
                        System.err.println( "relative: " + classesDir.relativize( file ) );
                        // Strip out .class from name
                        String name = file.getFileName().toString();
                        setClassName(name.substring(0, name.length() - 6));
                        setPackage( classesDir.relativize( file ).getParent().toString().replace(File.separatorChar, '.') );
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoFailureException("Unable to determine FractionDefaulter class", e);
        }
    }

    private void setClassName(String className) {
        this.className = className;
        String artifactId = this.project.getArtifactId();
        if (this.fractionModuleName == null && artifactId.startsWith(PREFIX)) {
            this.fractionModuleName = artifactId.substring(PREFIX.length());
        }
    }

    private void setPackage(String name) {
        this.packageName = name;
        System.err.println( " --------------- " + this.packageName );
    }

}
