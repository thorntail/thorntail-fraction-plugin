package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
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
import org.eclipse.aether.impl.ArtifactResolver;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class GenerateMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private String projectOutputDir;

    @Parameter(defaultValue = "${root-module}")
    private String rootModule;

    @Parameter(defaultValue = "${extra-modules}")
    private String extraModules;

    @Parameter(defaultValue = "${export-modules}")
    private String exportModules;

    @Parameter(defaultValue = "${fraction-module}")
    private String fractionModuleName;

    @Inject
    private ArtifactResolver resolver;

    private File featurePackDir;

    private String className;
    private String packageNameWithTrailingDot;

    private static final String PREFIX = "wildfly-swarm-";

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (rootModule == null || rootModule.length() == 0) {
            throw new MojoFailureException("This plugin requires the 'root-module' property to be set.");
        }

        determineClassName();

        if (fractionModuleName == null || fractionModuleName.length() == 0) {
            throw new MojoFailureException("This plugin requires the 'fraction-module' property to be set.");
        }

        generateServiceLoaderDescriptor();
        generateFeaturePack();
        generateFractionReferenceForJar();
        generateFeaturePackReferenceForJar();
    }

    private void generateFractionReferenceForJar() throws MojoFailureException {
        File reference = new File( this.projectOutputDir, "wildfly-swarm-fraction.gav" );

        try {
            FileWriter out = new FileWriter(reference);

            try {
                out.write(this.project.getGroupId() + ":" + this.project.getArtifactId() + ":zip:fraction:" + this.project.getVersion() + "\n");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException( "unable to create fraction reference for jar", e );
        }
    }

    private void generateFeaturePackReferenceForJar() throws MojoFailureException {
        String featurePack = this.project.getProperties().getProperty("feature-pack");
        if ( featurePack == null ) {
            return;
        }

        String[] parts = featurePack.split(":");
        List<Dependency> deps = this.project.getDependencyManagement().getDependencies();

        Dependency featurePackDep = null;
        for ( Dependency each : deps ) {
            if ( each.getGroupId().equals( parts[0] ) && each.getArtifactId().equals( parts[1] ) && each.getType().equals( "zip" ) ) {
                getLog().info( "Using feature-pack: " + each );
                featurePackDep = each;
                break;
            }
        }

        if ( featurePackDep == null ) {
            throw new MojoFailureException( "Unable to determine feature-pack: " + featurePack );
        }

        File reference = new File( this.projectOutputDir, "wildfly-swarm-feature-pack.gav" );

        try {
            FileWriter out = new FileWriter(reference);

            try {
                out.write( featurePackDep.getGroupId() + ":" + featurePackDep.getArtifactId() + ":zip:" + featurePackDep.getVersion() + "\n" );
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException( "unable to create feature-pack reference for jar", e );
        }
    }


    private void generateFeaturePack() throws MojoFailureException {
        generateModule();
        createZip();
    }


    private void createZip() throws MojoFailureException {
        File zipFile = new File(this.projectBuildDir, this.project.getArtifactId() + "-" + this.project.getVersion() + "-fraction.zip");
        zipFile.getParentFile().mkdirs();

        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

            try {
                walkZip(out, new File(this.projectBuildDir, "fraction"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create fraction.zip file", e);

        }

        org.apache.maven.artifact.DefaultArtifact zipArtifact = new org.apache.maven.artifact.DefaultArtifact(
                this.project.getGroupId(),
                this.project.getArtifactId(),
                this.project.getVersion(),
                "provided",
                "zip",
                "fraction",
                new DefaultArtifactHandler("zip")
        );

        zipArtifact.setFile(zipFile);

        this.project.addAttachedArtifact(zipArtifact);
    }

    private void walkZip(ZipOutputStream out, File file) throws IOException {

        if (!file.equals(this.featurePackDir)) {
            String zipPath = file.getAbsolutePath().substring(this.featurePackDir.getAbsolutePath().length() + 1);
            if (file.isDirectory()) {
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

                while ((len = in.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                in.close();
            }
        }
    }

    private void generateModule() throws MojoFailureException {
        this.featurePackDir = new File(this.projectBuildDir, "fraction");
        File dir = new File(this.featurePackDir, "modules/system/layers/base/" + this.project.getGroupId().replaceAll("\\.", "/") + "/" + fractionModuleName + "/main");
        dir.mkdirs();

        File moduleXml = new File(dir, "module.xml");

        try {

            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(moduleXml));

            out.write("<module xmlns=\"urn:jboss:module:1.3\" name=\"" + this.project.getGroupId() + "." + fractionModuleName + "\">\n");
            out.write("  <resources>\n");
            out.write("    <artifact name=\"${" + this.project.getGroupId() + ":" + this.project.getArtifactId() + "}\"/>\n");
            out.write("  </resources>\n");
            out.write("  <dependencies>\n");
            out.write("    <module name=\"org.wildfly.swarm.container\"/>\n");
            out.write("    <module name=\"" + this.rootModule + "\"/>\n");

            if (this.extraModules != null) {
                String[] names = this.extraModules.split("[\\s,]+");
                for (int i = 0; i < names.length; ++i) {
                    out.write("    <module name=\"" + names[i].trim() + "\"/>\n");
                }
            }

            if (this.exportModules != null) {
                String[] names = this.exportModules.split("[\\s,]+");
                for (int i = 0; i < names.length; ++i) {
                    out.write("    <module name=\"" + names[i].trim() + "\" export=\"true\"/>\n");
                }
            }
            out.write("  </dependencies>\n");
            out.write("</module>\n");

            try {

            } finally {
                out.close();
            }

        } catch (IOException e) {
            throw new MojoFailureException("Unable to create module.xml", e);
        }
    }

    private void generateServiceLoaderDescriptor() throws MojoFailureException {
        File dir = new File(this.projectBuildDir, "classes/META-INF/services");
        dir.mkdirs();

        File services = new File(dir, "org.wildfly.swarm.container.FractionDefaulter");

        try {
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(services));

            try {
                out.write(this.packageNameWithTrailingDot + this.className + "\n");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create services file: " + services, e);
        }
    }

    private void determineClassName() throws MojoFailureException {
        try {
            Files.walkFileTree((new File(this.projectOutputDir)).toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().equals("META-INF")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith("FractionDefaulter.class")) {
                        // Strip out .class from name
                        String name = file.getFileName().toString();
                        setClassName(name.substring(0, name.length() - 6));

                        String packageName = "";
                        Path current = file.getParent();
                        while (true) {
                            if (current.getFileName().toString().equals("classes")) {
                                setPackage(packageName);
                                break;
                            }

                            packageName = current.getFileName().toString() + "." + packageName;
                            current = current.getParent();
                        }
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MojoFailureException("Unable to determine FractionDefaulter class", e);
        }

        if (this.className == null || this.packageNameWithTrailingDot == null) {
            throw new MojoFailureException("Unable to determine FractionDefaulter class");
        }
    }

    private void setClassName(String className) {
        this.className = className;
        String artifactId = this.project.getArtifactId();
        if (this.fractionModuleName == null && artifactId.startsWith(PREFIX)) {
            this.fractionModuleName = artifactId.substring(PREFIX.length());
        }
    }

    private void setPackage(String name) {
        this.packageNameWithTrailingDot = name;
    }
}
