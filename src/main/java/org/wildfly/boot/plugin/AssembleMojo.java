package org.wildfly.boot.plugin;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

/**
 * @author Bob McWhirter
 */
@Mojo(
        name = "assemble",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class AssembleMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${basedir}")
    private String projectBaseDir;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${localRepository}")
    private ArtifactRepository localRepository;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

    @Inject
    private ArtifactResolver resolver;

    private File dir;
    private File m2repoDir;
    private File modulesDir;

    private Set<ArtifactSpec> artifacts = new HashSet<ArtifactSpec>();
    private Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.dir = new File(this.projectBuildDir, "feature-pack");
        this.m2repoDir = new File(this.dir, "m2repo");
        this.modulesDir = new File(this.dir, "modules");

        try {
            gatherModuleXmls();
            gatherDependencies();
            addFeaturePackTxt();
            createZip();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void createZip() throws Exception {
        File zipFile = new File(this.projectBuildDir, this.project.getArtifactId() + "-" + this.project.getVersion() + "-feature-pack.zip");
        zipFile.getParentFile().mkdirs();

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

        try {
            walkZip(out, this.dir);
        } finally {
            out.close();
        }

        org.apache.maven.artifact.DefaultArtifact zipArtifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "provided",
                "zip",
                "feature-pack",
                new DefaultArtifactHandler("zip")
        );

        zipArtifact.setFile( zipFile );

        this.project.addAttachedArtifact( zipArtifact );
    }

    private void walkZip(ZipOutputStream out, File file) throws IOException {

        if ( ! file.equals(this.dir ) ) {
            String zipPath = file.getAbsolutePath().substring(this.dir.getAbsolutePath().length() + 1);
            if ( file.isDirectory() ) {
                zipPath = zipPath + "/";
            }
            out.putNextEntry(new ZipEntry(zipPath));
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            for (int i = 0; i < children.length; ++i) {
                walkZip(out, children[i]);
            }
        } else {
            FileInputStream in = new FileInputStream(file);

            try {

                byte[] buf = new byte[1024];
                int len = -1;

                while ( ( len = in.read(buf))>=0) {
                    out.write(buf, 0, len );
                }
            } finally {
                in.close();
            }
        }
    }

    private void gatherDependencies() throws Exception {

        List<Dependency> dependencies = this.project.getDependencyManagement().getDependencies();

        for (Dependency each : dependencies) {
            ArtifactSpec spec = new ArtifactSpec(each.getGroupId() + ":" + each.getArtifactId());
            if (!this.artifacts.contains(spec)) {
                continue;
            }
            ArtifactRequest request = new ArtifactRequest();
            DefaultArtifact artifact = new DefaultArtifact(each.getGroupId(), each.getArtifactId(), each.getClassifier(), each.getType(), each.getVersion());
            request.setArtifact(artifact);
            try {
                ArtifactResult result = resolver.resolveArtifact(this.session, request);
                this.resolvedArtifacts.add(result.getArtifact());
            } catch (ArtifactResolutionException e) {
                // skip
            }
        }

        for (Artifact each : this.resolvedArtifacts) {
            File inFile = each.getFile();
            File outFile = mavenArtifactOutputFile(m2repoDir, each);
            copy(inFile, outFile);
        }
    }

    private File mavenArtifactOutputFile(File baseDir, Artifact artifact) {
        return new File(baseDir, artifact.getGroupId().replaceAll("\\.", "/") + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getExtension());
    }

    private void copy(File src, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        FileInputStream in = new FileInputStream(src);
        try {
            FileOutputStream out = new FileOutputStream(dest);

            try {
                byte[] buf = new byte[1024];
                int len = -1;

                while ((len = in.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }

        } finally {
            in.close();
        }
    }

    private void addFeaturePackTxt() throws IOException {
        File txt = new File( projectBaseDir, "src/main/resources/feature-pack.txt" );
        if ( txt.exists() ) {
            copy( txt, new File( this.dir, "feature-pack.txt" ) );
        }

    }

    private static final String MODULES_PREFIX = "src/main/resources/module";

    private void gatherModuleXmls() throws IOException {
        File root = new File(projectBaseDir, "src/main/resources/modules");
        walk(root);
    }

    private void walk(File file) throws IOException {
        if (file.getName().equals("module.xml")) {
            analyzeModuleXml(file);
            copy(file, moduleXmlOutputFile(file));
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            for (int i = 0; i < children.length; ++i) {
                walk(children[i]);
            }
        }
    }

    private File moduleXmlOutputFile(File in) {
        String prefix = new File(this.projectBaseDir, MODULES_PREFIX).getAbsolutePath();

        if (in.getAbsolutePath().startsWith(prefix)) {
            return new File(this.modulesDir, in.getAbsolutePath().substring(prefix.length() + 1));
        }

        return null;
    }

    private void analyzeModuleXml(File moduleXml) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(moduleXml)));

            try {

                String line = null;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("<artifact name=")) {
                        int start = line.indexOf("${");
                        if (start > 0) {
                            int end = line.indexOf('}', start);
                            if (end > 0) {
                                String artifact = line.substring(start + 2, end);
                                this.artifacts.add(new ArtifactSpec(artifact));
                            }
                        }
                    }
                }
            } finally {
                reader.close();
            }


        } catch (IOException e) {

        }
    }

    private static class ArtifactSpec {
        public String groupId;
        public String artifactId;

        public ArtifactSpec(String spec) {
            String[] parts = spec.split(":");
            this.groupId = parts[0];
            this.artifactId = parts[1];
        }

        public ArtifactSpec(Artifact artifact) {
            this.groupId = artifact.getGroupId();
            this.artifactId = artifact.getArtifactId();
        }

        public boolean equals(Object o) {
            if (!(o instanceof ArtifactSpec)) {
                return false;
            }

            return (this.groupId.equals(((ArtifactSpec) o).groupId) && this.artifactId.equals(((ArtifactSpec) o).artifactId));
        }

        @Override
        public int hashCode() {
            return this.groupId.hashCode() / 2 + this.artifactId.hashCode() / 2;
        }

        public String toString() {
            return this.groupId + ":" + this.artifactId;
        }
    }
}
