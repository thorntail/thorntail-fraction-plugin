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

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * @author Bob McWhirter
 */
public class BootstrapMarker {

    public static final String BOOTSTRAP_MARKER = "wildfly-swarm-bootstrap.conf";

    public BootstrapMarker(Log log,
                           MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public void execute() throws IOException {
        Path src = Paths.get(this.project.getBuild().getSourceDirectory());

        if (!Files.exists(src)) {
            return;
        }

        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith("Fraction.java")) {
                    fraction = file;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Resource dir : this.project.getBuild().getResources()) {
            Path dirPath = Paths.get( dir.getDirectory() );
            if ( Files.exists( dirPath ) && Files.isDirectory( dirPath ) ) {
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

        if (!this.bootstrapMarkerFound && this.fraction != null) {
            createBootstrapMarker();
        }
    }

    private void createBootstrapMarker() throws IOException {
        Path path = Paths.get(this.project.getBuild().getSourceDirectory()).relativize(this.fraction).getParent();

        String moduleName = path.toString().replaceAll(File.separator, ".");

        try (FileWriter writer = new FileWriter(new File(this.project.getBuild().getOutputDirectory(), BOOTSTRAP_MARKER))) {
            writer.write(moduleName);
            writer.write("\n");
            writer.flush();
        }
    }

    private final Log log;

    private final MavenProject project;

    private Path fraction;

    private boolean bootstrapMarkerFound;

}
