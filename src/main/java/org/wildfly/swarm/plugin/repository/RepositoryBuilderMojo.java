package org.wildfly.swarm.plugin.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

/**
 * @author Ken Finnigan
 */
@Mojo(name = "build-repository",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class RepositoryBuilderMojo extends ProjectBuilderMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // Execute parent Mojo that will eventually generate project from bom
        super.execute();

        try {
            repoDir = new File(this.project.getBuild().getDirectory(), "repository");
            if (!repoDir.mkdirs()) {
                String[] repoDirFiles = repoDir.list();
                if (repoDir.exists() && (repoDirFiles != null && repoDirFiles.length > 0)) {
                    getLog().info("Repository already created, using existing content.");
                    return;
                }
            }

            // Load project dependencies into local M2 repo
            executeGeneratedProjectBuild(repoPomFile, projectDir, repoDir);

            // Clear out unnecessary files from local M2 repo
            santizeRepo(repoDir.toPath());

            if (generateZip) {
                // Zip local M2 repo
                File repoZip = new File(this.project.getBuild().getDirectory(), this.project.getArtifactId() + "-" + this.project.getVersion() + ".zip");

                try (FileOutputStream fos = new FileOutputStream(repoZip);
                     ZipOutputStream zipOut = new ZipOutputStream(fos)) {
                    zipFile(repoDir, repoDir.getName(), zipOut);
                }

                // Attach zip of M2 repo to Maven Project
                projectHelper.attachArtifact(this.project, "zip", repoZip);
                getLog().info("Attached M2 Repo zip as project artifact.");
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void executeGeneratedProjectBuild(File pomFile, File projectDir, File repoDir) throws Exception {
        InvocationRequest mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(pomFile);
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

    private void santizeRepo(Path repoDirPath) throws IOException {
        getLog().info("Remove unneeded files from local M2 Repo, " + repoDirPath.toAbsolutePath());

        Files.walkFileTree(repoDirPath, new SimpleFileVisitor<Path>() {
            private boolean pruneDirectory = false;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // If we need to prune communtiy artifacts, ie those from Central, then remove from repository
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

    protected boolean isProductizedArtifact(Path file) {
        return file.toString().contains("redhat-");
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

    boolean generateZip = true;

    File repoDir;
}
