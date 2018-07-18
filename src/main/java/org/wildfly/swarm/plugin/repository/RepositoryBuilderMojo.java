package org.wildfly.swarm.plugin.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.wildfly.swarm.plugin.AbstractFractionsMojo;
import org.wildfly.swarm.plugin.RepositoryUtils;

import javax.inject.Inject;

/**
 * @author Ken Finnigan
 */
@Mojo(name = "build-repository",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class RepositoryBuilderMojo extends AbstractFractionsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ProjectBuilder projectBuilder = new ProjectBuilder(project, getLog(), template);
        /*
                1. build repository for bom
                    - generate project for bom contents only
                    - build the project
                    - prepare runtime artifacts analysis for it
                    - save the original repo zip contents
                2. add repo artifacts from additional bom
                    - generate project for bom + additional bom
                    - build the project pointing to the same local repo
                3. create the repo zip
        */
        try {
            repoDir = new File(this.project.getBuild().getDirectory(), "repository");
            if (!repoDir.mkdirs()) {
                String[] repoDirFiles = repoDir.list();
                if (repoDir.exists() && (repoDirFiles != null && repoDirFiles.length > 0)) {
                    getLog().info("Repository already created, using existing content.");
                    return;
                }
            }

            addBom();

            File currentBom = getBomFile();
            File currentBomProject = projectBuilder.generateProject(currentBom);

            executeGeneratedProjectBuild(currentBomProject, repoDir);

            if (isAnalyzeRuntimeDependencies()) {
                generateRuntimeDependenciesDescriptor(currentBomProject);
            }

            if (StringUtils.isNotBlank(additionalBom)) {
                File currentAndAdditionalBomProject = projectBuilder.generateProject(currentBom, getPom(additionalBom));
                executeGeneratedProjectBuild(currentAndAdditionalBomProject, repoDir);
            }

            // Clear out unnecessary files from local M2 repo
            santizeRepo(repoDir.toPath());

            if (shouldGenerateZip()) {
                generateRepositoryZip();
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected boolean shouldGenerateZip() {
        return generateZip == null || generateZip.trim().isEmpty()
                ? defaultGenerateZip
                : Boolean.parseBoolean(generateZip);
    }


    public File getPom(String gavString) throws ArtifactResolutionException {
        String[] gav = gavString.split(":");
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
        return result.getArtifact().getFile();
    }

    private File getBomFile() throws MojoFailureException {
        final File bomFile = new File(this.project.getBuild().getOutputDirectory(), "bom.pom");

        if (!bomFile.exists()) {
            throw new MojoFailureException("No bom.pom file found in target directory. Please add `generate-bom` goal of fraction-plugin to project.");
        }
        return bomFile;
    }

    private void generateRuntimeDependenciesDescriptor(File projectDir) throws MojoExecutionException, IOException {
        File projectTargetDir = new File(projectDir, "target");
        String[] swarmJars = projectTargetDir.list((dir, name) -> name.endsWith("-thorntail.jar"));
        if (swarmJars.length != 1) {
            throw new MojoExecutionException("Invalid number of -thorntail.jar's generated. " +
                    "Expecting 1 jar, found: " + Arrays.toString(swarmJars));
        }
        File jar = new File(projectTargetDir, swarmJars[0]);
        ZipFile jarAsZipFile = new ZipFile(jar);
        String m2repoContentsList =
                Collections.list(jarAsZipFile.entries())
                        .stream()
                        .filter(zipEntry -> !zipEntry.isDirectory())
                        .map(ZipEntry::getName)
                        .filter(name -> name.startsWith(M2REPO))
                        .map(name -> name.substring(M2REPO.length()))
                        .collect(Collectors.joining("\n"));
        File m2ContentsDecriptor =
                new File(this.project.getBuild().getDirectory(), this.project.getArtifactId() + "-" + this.project.getVersion() + ".txt");
        try (FileWriter writer = new FileWriter(m2ContentsDecriptor)) {
            writer.write(m2repoContentsList);
        }

        projectHelper.attachArtifact(this.project, "txt", "runtime-dependencies", m2ContentsDecriptor);
    }

    private void generateRepositoryZip() throws IOException {
        // Zip local M2 repo
        File repoZip = new File(this.project.getBuild().getDirectory(), this.project.getArtifactId() + "-" + this.project.getVersion() + ".zip");

        try (FileOutputStream fos = new FileOutputStream(repoZip);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            zipFile(repoDir, repoDir.getName(), zipOut);
        }

        // Attach zip of M2 repo to Maven Project
        projectHelper.attachArtifact(this.project, "zip", "maven-repository", repoZip);
        getLog().info("Attached M2 Repo zip as project artifact.");
    }

    private void executeGeneratedProjectBuild(File projectDir, File repoDir) throws Exception {
        InvocationRequest mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(new File(projectDir, "pom.xml"));
        mavenRequest.setBaseDirectory(projectDir);
        mavenRequest.setUserSettingsFile(userSettings);
        mavenRequest.setLocalRepositoryDirectory(repoDir);
        mavenRequest.setGoals(Collections.singletonList("install"));

        Properties props = System.getProperties();

        if (Boolean.parseBoolean(downloadSources)) {
            props.setProperty("swarm.download.sources", "");
        }

        if (Boolean.parseBoolean(downloadPoms)) {
            props.setProperty("swarm.download.poms", "");
        }

        mavenRequest.setProperties(props);

        Invoker invoker = new DefaultInvoker();
        InvocationResult result = invoker.execute(mavenRequest);

        if (result.getExitCode() != 0) {
            throw result.getExecutionException();
        }

        getLog().info("Built project from BOM: " + projectDir.getAbsolutePath());
    }

    private void addBom() throws MojoFailureException, IOException {
        getLog().info("Add the bom to the repository");

        String[] groupIdParts = project.getGroupId().split("\\.");

        FileSystem fs = FileSystems.getDefault();
        Path groupIdDir = fs.getPath(repoDir.getAbsolutePath(), groupIdParts);
        Path targetDir = fs.getPath(groupIdDir.toString(), project.getArtifactId(), project.getVersion());
        String fileName = String.format("%s-%s.%s",
                project.getArtifactId(), project.getVersion(), "pom"
        );

        File targetFile = new File(targetDir.toFile(), fileName);

        FileUtils.copyFile(getBomFile(), targetFile);
        getLog().info("Copied the bom to " + targetFile.getAbsolutePath());
    }

    private void santizeRepo(Path repoDirPath) throws IOException {
        getLog().info("Remove unneeded files from local M2 Repo, " + repoDirPath.toAbsolutePath());

        Files.walkFileTree(repoDirPath, new SimpleFileVisitor<Path>() {
            private boolean pruneDirectory = false;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                // If we need to prune community artifacts, ie those from Central, then remove from repository
                if (!pruneDirectory && isRemoveCommunity() && !isProductizedArtifact(file) && !isUnneeded(file)) {
                    pruneDirectory = true;
                }
                if (pruneDirectory) {
                    Files.delete(file);
                } else if (isUnneeded(file)) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null && pruneDirectory) {
                    pruneDirectory = false;
                    Files.delete(dir);
                } else if (dir.toFile().listFiles().length == 0) {
                    Files.delete(dir);
                }
                return super.postVisitDirectory(dir, exc);
            }

            private boolean isUnneeded(Path file) {
                return file.endsWith("_remote.repositories") || file.toString().endsWith(".lastUpdated");
            }

        });
    }

    protected boolean isRemoveCommunity() {
        return Boolean.parseBoolean(removeCommunity);
    }

    protected boolean isAnalyzeRuntimeDependencies() {
        return Boolean.parseBoolean(analyzeRuntimeDependencies);
    }

    protected static boolean isProductizedArtifact(Path file) {
        return isProductizedArtifact(file.toString());
    }

    protected static boolean isProductizedArtifact(String name) {
        return name.contains("redhat-") || name.contains("eap-runtime-artifacts");
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }

        if (fileToZip.isDirectory()) {
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
    }

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter
    private String downloadSources;

    @Parameter
    private String downloadPoms;

    @Parameter
    protected File userSettings;

    @Parameter(defaultValue = "false")
    private String removeCommunity;

    @Parameter(defaultValue = "false")
    protected String analyzeRuntimeDependencies;

    @Parameter
    protected String generateZip;

    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession session;

    @Parameter
    private String additionalBom;

    @Parameter
    private File template;

    boolean defaultGenerateZip = true;

    File repoDir;

    @Inject
    private ArtifactResolver resolver;

    public static final String M2REPO = "m2repo/";
}
