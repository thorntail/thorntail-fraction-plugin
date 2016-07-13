package org.wildfly.swarm.plugin;

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

    public static StabilityLevel parse(String level) {
        level = level.toUpperCase().trim();
        try {
            int levelInt = Integer.parseInt( level );
            return StabilityLevel.values()[levelInt];
        } catch (NumberFormatException e) {
            // oh well, try it as a word.
        }
        return StabilityLevel.valueOf(level);
    }
}
