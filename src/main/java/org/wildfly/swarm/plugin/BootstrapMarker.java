package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * @author Bob McWhirter
 */
public class BootstrapMarker {

    public static final String BOOTSTRAP_PROPERTY = "swarm.fraction.bootstrap";

    public static final String BOOTSTRAP_MARKER = "wildfly-swarm-bootstrap.conf";

    public BootstrapMarker(Log log,
                           MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public static Path baseModulePath(MavenProject project) throws IOException {

        String pomBootstrap = project.getProperties().getProperty(BOOTSTRAP_PROPERTY);

        Path moduleConf = Paths.get(project.getBasedir().getAbsolutePath(), "module.conf");

        if (!Files.exists(moduleConf) && pomBootstrap == null) {
            System.err.println( "no pomBootstrap or module.conf" );
            return null;
        }

        Path src = Paths.get(project.getBuild().getSourceDirectory());

        AtomicReference<Path> root = new AtomicReference<>();

        if (Files.exists(src)) {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith("Fraction.java")) {
                        Path path = src.relativize(file.getParent());
                        System.err.println( "found Fraction.java at " + path );
                        root.set(path);
                    }
                    return super.visitFile(file, attrs);
                }
            });
        }

        Path path = root.get();

        System.err.println( "path is before: " + path );

        if (path == null) {
            System.err.println( "generating path from groupId/artifactId" );
            path = Paths.get(project.getGroupId().replace('.', File.separatorChar) + File.separatorChar + project.getArtifactId().replace('-', File.separatorChar));
        }

        System.err.println( "return path: " + path );

        return path;
    }

    public void execute() throws IOException {

        String pomBootstrap = this.project.getProperties().getProperty(BOOTSTRAP_PROPERTY);

        Path src = Paths.get(this.project.getBuild().getSourceDirectory());

        if (Files.exists(src)) {
            for (Resource dir : this.project.getBuild().getResources()) {
                Path dirPath = Paths.get(dir.getDirectory());
                if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                    Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (file.getFileName().toString().equals(BOOTSTRAP_MARKER)) {
                                bootstrapMarkerFound = true;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        }

        if (this.bootstrapMarkerFound) {
            if (pomBootstrap != null) {
                this.log.warn("Both a file named " + BOOTSTRAP_MARKER + " and the property " + BOOTSTRAP_PROPERTY + " were set.  Ignoring property, preferring file");
            }
            return;
        }

        Path path = baseModulePath(this.project);

        if (path != null || pomBootstrap != null) {
            createBootstrapMarker(path, pomBootstrap);
        }
    }

    private void createBootstrapMarker(Path modulePath, String moduleName) throws IOException {
        if (moduleName == null) {
            moduleName = modulePath.toString().replace(File.separator, ".");
            this.log.info("Using " + moduleName + " as conventional bootstrap module name");
        }

        if (moduleName.equalsIgnoreCase("true")) {
            moduleName = "";
        }

        try (FileWriter writer = new FileWriter(new File(this.project.getBuild().getOutputDirectory(), BOOTSTRAP_MARKER))) {
            writer.write(moduleName);
            writer.write("\n");
            writer.flush();
        }
    }

    private final Log log;

    private final MavenProject project;

    private boolean bootstrapMarkerFound;

}
