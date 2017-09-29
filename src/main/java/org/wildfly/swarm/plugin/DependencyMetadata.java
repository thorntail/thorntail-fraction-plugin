package org.wildfly.swarm.plugin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Bob McWhirter
 */
public class DependencyMetadata implements Comparable<DependencyMetadata> {


    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String classifier;

    private final String packaging;

    private final Scope scope;

    DependencyMetadata(FractionMetadata meta) {
        this(meta.getGroupId(), meta.getArtifactId(), meta.getVersion(), meta.getClassifier(), meta.getPackaging(), meta.getScope());
    }

    public static DependencyMetadata fromMscGav(String gav) {
        String[] parts = gav.split(":");
        if (parts.length == 3) {
            return new DependencyMetadata(parts[0], parts[1], parts[2], null, "jar", null);
        } else if (parts.length == 4) {
            return new DependencyMetadata(parts[0], parts[1], parts[2], parts[3], "jar", null);
        } else {
            throw new RuntimeException("Invalid gav: " + gav);
        }

    }

    public DependencyMetadata(String groupId, String artifactId, String version, String classifier, String packaging) {
        this(groupId, artifactId, version, classifier, packaging, null);
    }

    public DependencyMetadata(String groupId, String artifactId, String version, String classifier, String packaging, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.packaging = packaging;
        this.scope = Scope.of(scope);
    }

    public String getGroupId() {
        return this.groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getVersion() {
        return this.version;
    }

    @JsonIgnore
    public String getClassifier() {
        return this.classifier;
    }

    @JsonIgnore
    public String getPackaging() {
        return this.packaging;
    }

    @JsonIgnore
    public String getScope() {
        return scope.getValue();
    }

    @JsonIgnore
    public boolean hasDefaultScope() {
        return Scope.COMPILE.equals(scope);
    }

    @JsonInclude(value = Include.NON_NULL)
    @JsonProperty("scope")
    public String jsonScope() {
        return hasDefaultScope() ? null : getScope();
    }

    public static DependencyMetadata fromString(String gav) {
        String[] parts = gav.split(":");

        if (parts.length == 4) {
            return new DependencyMetadata(parts[0], parts[1], parts[3], null, parts[2]);
        } else {
            return new DependencyMetadata(parts[0], parts[1], parts[4], parts[3], parts[2]);
        }
    }

    @Override
    public String toString() {
        return this.groupId + ":" + this.artifactId + ":" + this.packaging + (this.classifier == null ? "" : ":" + this.classifier) + ":" + this.version;
    }

    @Override
    public int compareTo(DependencyMetadata that) {
        return this.toString().compareTo(that.toString());
    }

    @Override
    public boolean equals(Object that) {
        return this.toString().equals(that.toString());
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    enum Scope {

        COMPILE, TEST, RUNTIME, PROVIDED, SYSTEM, IMPORT;

        String getValue() {
            return toString().toLowerCase();
        }

        static Scope of(String scope) {
            if (scope != null) {
                scope = scope.toUpperCase().trim();
                for (Scope value : Scope.values()) {
                    if (value.toString().equals(scope)) {
                        return value;
                    }
                }
            }
            return COMPILE;
        }

    }

}
