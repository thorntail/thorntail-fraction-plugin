package org.wildfly.swarm.plugin.repository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;

/**
 * @author Ken Finnigan
 */
@Mojo(name = "generate-project",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class ProjectBuilderMojo extends AbstractFractionsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            // Initialize the project dir first
            projectDir = new File(this.project.getBuild().getDirectory(), "generated-project");
            if (!projectDir.mkdirs()) {
                if (projectDir.exists()) {
                    // Mojo already run, skip
                    return;
                } else {
                    throw new MojoFailureException("Unable to create " + projectDir.getCanonicalPath());
                }
            }

            if (pomFile == null || !pomFile.canRead()) {
                // There is no pom.xml specified - generate one from BOM
                final File bomFile = getBomFile();
                if (!template.exists()) {
                    throw new MojoFailureException("Unable to proceed without a `template` specified for generating a project pom.xml.");
                }

                repoPomFile = BomProjectBuilder.generateProject(projectDir, bomFile, template, project);
                if (!repoPomFile.exists()) {
                    throw new MojoFailureException("Failed to create project pom.xml");
                }
                getLog().info("Generated pom.xml from BOM: " + repoPomFile.getAbsolutePath());
            } else {
                // Use the specified pom.xml
                repoPomFile = new File(projectDir, "pom.xml");
                Files.copy(pomFile.toPath(), repoPomFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLog().info("Copied pom.xml from existing: " + repoPomFile.getAbsolutePath());
            }

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected File getBomFile() throws MojoFailureException {
        final File bomFile = new File(this.project.getBuild().getOutputDirectory(), "bom.pom");

        if (!bomFile.exists()) {
            throw new MojoFailureException("No bom.pom file find in target directory. Please add `generate-bom` goal of fraction-plugin to project.");
        }
        return bomFile;
    }

    @Parameter
    private File template;

    @Parameter
    protected File pomFile;

    protected File repoPomFile;

    File projectDir;

}
