package org.wildfly.swarm.plugin.process;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Function;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.FractionMetadata;

/**
 * @author Ken Finnigan
 */
public class CDIMarker implements Function<FractionMetadata, FractionMetadata> {

    private static final String CDI_MARKER = "META-INF/beans.xml";

    public CDIMarker(Log log, MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public FractionMetadata apply(FractionMetadata meta) {
        if (meta.hasJavaCode()) {
            File cdiMarker = new File(this.project.getBuild().getOutputDirectory(), CDI_MARKER);
            cdiMarker.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(cdiMarker)) {
                writer.write("<beans xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\n" +
                        "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "       xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee \n" +
                        "\t\thttp://xmlns.jcp.org/xml/ns/javaee/beans_1_1.xsd\"\n" +
                        "       bean-discovery-mode=\"annotated\">\n" +
                        "</beans>");
                writer.flush();
            } catch (IOException e) {
                this.log.error(e.getMessage(), e);
            }
        }

        return meta;
    }

    private final MavenProject project;

    private final Log log;
}
