package org.wildfly.swarm.plugin;

import org.apache.maven.project.MavenProject;

import static org.wildfly.swarm.plugin.AbstractFractionsMojo.FRACTION_INTERNAL_PROPERTY_NAME;
import static org.wildfly.swarm.plugin.AbstractFractionsMojo.FRACTION_TAGS_PROPERTY_NAME;
import static org.wildfly.swarm.plugin.StabilityLevel.FRACTION_STABILITY_PROPERTY_NAME;

/**
 * @author Bob McWhirter
 */
public class FractionDetector {

    private FractionDetector() {
    }

    public static boolean isFraction(MavenProject project) {
        boolean result = project.getProperties().getProperty(FRACTION_STABILITY_PROPERTY_NAME) != null
                || project.getProperties().getProperty(FRACTION_TAGS_PROPERTY_NAME) != null
                || project.getProperties().getProperty(FRACTION_INTERNAL_PROPERTY_NAME) != null;
        return result;
    }
}
