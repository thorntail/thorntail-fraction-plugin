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
                writer.write("");
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
