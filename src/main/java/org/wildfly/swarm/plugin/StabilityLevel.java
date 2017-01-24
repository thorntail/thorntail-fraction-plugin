package org.wildfly.swarm.plugin;

import java.util.Properties;

import org.apache.maven.project.MavenProject;

/**
 * @author Bob McWhirter
 */
public enum StabilityLevel {

    DEPRECATED,
    EXPERIMENTAL,
    UNSTABLE,
    STABLE,
    FROZEN,
    LOCKED;

    protected static final String DEFAULT_STABILITY_INDEX = "unstable";

    protected static final String FRACTION_STABILITY_PROPERTY_NAME = "swarm.fraction.stability";

    public static StabilityLevel parse(String level) {
        level = level.toUpperCase().trim();
        try {
            int levelInt = Integer.parseInt(level);
            return StabilityLevel.values()[levelInt];
        } catch (NumberFormatException e) {
            // oh well, try it as a word.
        }
        return StabilityLevel.valueOf(level);
    }

    public static StabilityLevel of(MavenProject project) {
        Properties properties = project.getProperties();
        return StabilityLevel.parse(properties.getProperty(FRACTION_STABILITY_PROPERTY_NAME, DEFAULT_STABILITY_INDEX));
    }
}
