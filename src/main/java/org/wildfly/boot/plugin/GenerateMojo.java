package org.wildfly.boot.plugin;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
import org.eclipse.aether.impl.ArtifactResolver;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

/**
 * @author Bob McWhirter
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

    @Parameter(defaultValue = "${basedir}")
    private String projectBaseDir;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(defaultValue = "${project.build.outputDirectory}" )
    private String projectOutputDir;

    @Parameter(defaultValue = "${localRepository}")
    private ArtifactRepository localRepository;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

    @Inject
    private ArtifactResolver resolver;

    private File featurePackDir;

    private static final String PREFIX = "wildfly-boot-";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        String artifactId = project.getArtifactId();
        if (!artifactId.startsWith(PREFIX)) {
            throw new MojoFailureException("This plugin only works with wildfly-boot-* artifacts");
        }

        String simpleName = artifactId.substring(PREFIX.length());
        String packageName = packagize(simpleName);

        generateServiceLoaderDescriptor(packageName, simpleName);
        generateFeaturePack(simpleName);
    }

    private void generateFeaturePack(String name) throws MojoFailureException {
        generateModule(name);
        createZip();
    }


    private void createZip() throws MojoFailureException {
        File zipFile = new File(this.projectBuildDir, this.project.getArtifactId() + "-" + this.project.getVersion() + "-fraction.zip");
        zipFile.getParentFile().mkdirs();

        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

            try {
                walkZip(out, new File( this.projectBuildDir, "fraction" ) );
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException( "Unable to create fraction.zip file", e );

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

    private void generateModule(String name) throws MojoFailureException {
        this.featurePackDir = new File( this.projectBuildDir, "fraction" );
        File dir = new File( this.featurePackDir, "modules/system/layers/base/org/wildfly/boot/" + name + "/main");
        dir.mkdirs();

        File moduleXml = new File(dir, "module.xml");

        try {

            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(moduleXml));

            out.write("<module xmlns=\"urn:jboss:module:1.3\" name=\"org.wildfly.boot." + name + "\">\n");
            out.write("  <resources>\n");
            out.write("    <artifact name=\"${org.wildfly.boot:wildfly-boot-" + name + "}\"/>\n");
            out.write("  </resources>\n");
            out.write("  <dependencies>\n");
            out.write("    <module name=\"org.wildfly.boot.container\"/>\n");
            out.write("    <module name=\"" + this.project.getProperties().getProperty("root-module") + "\"/>\n");

            String extraModules = this.project.getProperties().getProperty( "extra-modules" );
            if ( extraModules != null ) {
                String[] names = extraModules.split("[\\s,]+");
                for ( int i = 0 ; i < names.length ; ++i ) {
                    out.write("    <module name=\"" + names[i].trim() + "\"/>\n");
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

    private void generateServiceLoaderDescriptor(String packageName, String simpleName) throws MojoFailureException {
        String actualClassName = determineActualClassName(packageName, simpleName);
        File dir = new File(this.projectBuildDir, "classes/META-INF/services");
        dir.mkdirs();

        File services = new File(dir, "org.wildfly.boot.container.SubsystemDefaulter");

        try {
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(services));

            try {
                out.write( actualClassName + "\n" );
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoFailureException("Unable to create services file: " + services, e);
        }
    }

    private String determineActualClassName(String packageName, String simpleName) throws MojoFailureException {
        File dir = new File( this.projectOutputDir, packageName.replaceAll( "\\.", "/" ) );

        simpleName = simpleName.replaceAll( "-", "" );

        File[] children = dir.listFiles();

        for ( int i = 0 ; i < children.length ; ++i ) {
            String childName = children[i].getName();
            if ( childName.equalsIgnoreCase(simpleName + "SubsystemDefaulter.class") ) {
                return packageName + "." + childName.substring( 0, childName.length() - 6 );
            }
        }

        throw new MojoFailureException( "Unable to determine SubsystemDefaulter class" );
    }

    /*
    private String camelize(String name) {
        char[] chars = name.toCharArray();

        StringBuffer camel = new StringBuffer();

        boolean start = true;

        for (int i = 0; i < chars.length; ++i) {
            if (start) {
                camel.append(Character.toUpperCase(chars[i]));
                start = false;
                continue;
            }

            if (chars[i] == '-') {
                start = true;
                continue;
            }

            camel.append(chars[i]);
        }

        return camel.toString();
    }
    */

    private String packagize(String name) {
        return "org.wildfly.boot." + name.replaceAll("-", ".");
    }

}
