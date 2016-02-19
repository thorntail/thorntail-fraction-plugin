package org.wildfly.swarm.plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bob McWhirter
 */
public class Fraction {

    private final String groupId;

    private final String artifactId;

    private final Set<Fraction> dependencies = new HashSet<>();

    public Fraction(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public void addDependency(Fraction fraction) {
        this.dependencies.add(fraction);
    }

    public Set<Fraction> getDependencies() {
        return this.dependencies;
    }

    public String toString() {
        return this.groupId + ":" + this.artifactId;
    }

    public String getDependenciesString() {
        return String.join(", ", this.dependencies.stream().map(e -> e.toString())
                .collect(Collectors.toList()));
    }
}
