package org.wildfly.swarm.plugin;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author Bob McWhirter
 */
public class DependencyMetadata {

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String classifier;

    private final String packaging;

    DependencyMetadata(FractionMetadata meta) {
        this.groupId = meta.getGroupId();
        this.artifactId = meta.getArtifactId();
        this.version = meta.getVersion();
        this.classifier = meta.getClassifier();
        this.packaging = meta.getClassifier();
    }

    public DependencyMetadata(String groupId, String artifactId, String version, String classifier, String packaging) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.packaging = packaging;
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

    @Override
    public String toString() {
        return this.groupId + ":" + this.artifactId + ":" + this.packaging + ( this.classifier == null ? "" : ":" + this.classifier ) + ":" + this.version;
    }
}
