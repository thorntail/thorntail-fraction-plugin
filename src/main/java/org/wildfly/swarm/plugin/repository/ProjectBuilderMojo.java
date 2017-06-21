package org.wildfly.swarm.plugin.repository;

import java.io.File;

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
        final File bomFile = new File(this.project.getBuild().getOutputDirectory(), "bom.pom");

        if (!bomFile.exists()) {
            throw new MojoFailureException("No bom.pom file find in target directory. Please add `generate-bom` goal of fraction-plugin to project.");
        }

        if (!template.exists()) {
            throw new MojoFailureException("Unable to proceed without a `template` specified for generating a project pom.xml.");
        }

        try {
            projectDir = new File(this.project.getBuild().getDirectory(), "generated-project");
            if (!projectDir.mkdirs()) {
                if (projectDir.exists()) {
                    // Mojo already run, skip
                    return;
                } else {
                    throw new MojoFailureException("Unable to create " + projectDir.getCanonicalPath());
                }
            }

            pomFile = BomProjectBuilder.generateProject(projectDir, bomFile, template);
            if (!pomFile.exists()) {
                throw new MojoFailureException("Failed to create project pom.xml");
            }

            getLog().info("Generated pom.xml for project with BOM: " + pomFile.getAbsolutePath());
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    @Parameter
    private File template;

    File projectDir;

    File pomFile;
}
