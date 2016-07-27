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
import org.apache.maven.project.MavenProject;

/**
 * @author Ken Finnigan
 */
public class CDIMarker {

    public static final String CDI_PROPERTY = "swarm.fraction.cdi";

    public static final String CDI_MARKER = "META-INF/beans.xml";

    public CDIMarker(MavenProject project) {
        this.project = project;
    }

    public void execute() throws IOException {
        String pomCDI = this.project.getProperties().getProperty(CDI_PROPERTY);

        Path output = Paths.get(this.project.getBuild().getOutputDirectory());

        if (!Files.exists(output) || (pomCDI != null && !Boolean.parseBoolean(pomCDI))) {
            return;
        }

        for (Resource dir : this.project.getBuild().getResources()) {
            Path dirPath = Paths.get(dir.getDirectory());
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.getFileName().toString().endsWith(CDI_MARKER)) {
                            cdiMarkerFound = true;
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }

        if (this.cdiMarkerFound) {
            return;
        }

        createCDIMarker();
    }

    private void createCDIMarker() throws IOException {
        File cdiMarker = new File(this.project.getBuild().getOutputDirectory(), CDI_MARKER);
        cdiMarker.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(cdiMarker)) {
            writer.write("");
            writer.flush();
        }
    }

    private final MavenProject project;

    private boolean cdiMarkerFound = false;
}
