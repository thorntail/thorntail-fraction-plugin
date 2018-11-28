package org.wildfly.swarm.plugin.process;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.FractionMetadata;

/**
 * @author Ken Finnigan
 */
public class DetectClassRemover {

    public DetectClassRemover(Log log, MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public FractionMetadata apply(FractionMetadata meta) throws MojoExecutionException {
        String outputDirStr = project.getBuild().getOutputDirectory();

        Path outputDir = Paths.get(outputDirStr);

        try {
            Files.walkFileTree(outputDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (dir.getFileName().toString().equals("detect")) {
                        Files.delete(dir);
                    }
                    return super.postVisitDirectory(dir, exc);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = outputDir.relativize(file);
                    Path cur = relative;
                    while (cur != null) {
                        if (cur.getFileName().toString().equals("detect")) {
                            Files.delete(file);
                        }
                        cur = cur.getParent();
                    }

                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed removing 'detect' classes from " + outputDirStr, e);
        }
        return meta;
    }

    private final MavenProject project;

    private final Log log;
}
