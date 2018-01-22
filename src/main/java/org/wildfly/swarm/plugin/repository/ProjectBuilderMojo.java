package org.wildfly.swarm.plugin.repository;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.RepositoryUtils;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * @author Ken Finnigan
 */
@Mojo(name = "generate-project",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class ProjectBuilderMojo extends AbstractFractionsMojo {

    @Inject
    private ArtifactResolver resolver;

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

                if (additionalBom != null && !"".equals(additionalBom)) {
                    String[] gav = additionalBom.split(":");
                    Artifact additionalBomArtifact = new DefaultArtifact(
                            gav[0],
                            gav[1],
                            null,
                            "pom",
                            gav[3]
                    );

                    List<RemoteRepository> repositories = RepositoryUtils.prepareRepositories(remoteRepositories);
                    ArtifactRequest request = new ArtifactRequest(additionalBomArtifact, repositories, null);
                    ArtifactResult result = resolver.resolveArtifact(session, request);
                    additionalBomFile = result.getArtifact().getFile();
                }

                repoPomFile = BomProjectBuilder.generateProject(projectDir, bomFile, template, project, skipBomDependencies, additionalBomFile);
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
            throw new MojoFailureException("No bom.pom file found in target directory. Please add `generate-bom` goal of fraction-plugin to project.");
        }
        return bomFile;
    }

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession session;

    @Parameter
    private String additionalBom;

    @Parameter
    private File template;

    @Parameter
    protected File pomFile;

    /**
     * List of expressions used to filter BOM dependencies.
     */
    @Parameter
    private String[] skipBomDependencies;

    protected File additionalBomFile;

    protected File repoPomFile;

    File projectDir;

}
