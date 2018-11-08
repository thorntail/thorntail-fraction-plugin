package org.wildfly.swarm.plugin.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.FractionMetadata;

/**
 * @author Bob McWhirter
 */
public class ReadmeGrabber {

    private final MavenProject project;

    public ReadmeGrabber(MavenProject project) {
        this.project = project;
    }

    public FractionMetadata apply(FractionMetadata meta) throws MojoExecutionException {
        if (!meta.isFraction()) {
            return meta;
        }

        File readme = new File(project.getBasedir(), "README.adoc");

        if (!readme.exists()) {
            return meta;
        }

        Path destination = Paths.get(project.getBuild().getOutputDirectory()).resolve("META-INF").resolve("README.adoc");

        try {
            Files.createDirectories(destination.getParent());
            Files.copy(readme.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed copying README.adoc", e);
        }

        return meta;
    }
}
