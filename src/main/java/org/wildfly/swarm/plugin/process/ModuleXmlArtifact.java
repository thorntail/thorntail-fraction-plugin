package org.wildfly.swarm.plugin.process;

import org.eclipse.aether.artifact.Artifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maven artifact coordinates in the JBoss Modules style ({@code groupId:artifactId:version[:classifier]}).
 * See {@code org.jboss.modules.maven.ArtifactCoordinates}.
 */
public class ModuleXmlArtifact {
    // split by ':', unless it's enclosed in ${...}
    private static final Pattern PARSE = Pattern.compile("((\\$\\{[^}]*})|([^:]+))");

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;

    public static ModuleXmlArtifact from(Artifact artifact) {
        return new ModuleXmlArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier());
    }

    public static ModuleXmlArtifact parse(String string) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = PARSE.matcher(string);
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }

        if (parts.size() == 3) {
            return new ModuleXmlArtifact(parts.get(0), parts.get(1), parts.get(2), "");
        } else if (parts.size() == 4) {
            return new ModuleXmlArtifact(parts.get(0), parts.get(1), parts.get(2), parts.get(3));
        } else {
            throw new IllegalArgumentException("Invalid artifact specifier '" + string
                    + "', expected groupId:artifactId:version[:classifier]");
        }
    }

    private ModuleXmlArtifact(String groupId, String artifactId, String version, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
    }

    @Override
    public String toString() {
        String result = groupId + ":" + artifactId + ":" + version;
        return classifier.isEmpty() ? result : (result + ":" + classifier);
    }

    public boolean equalsIgnoringVersion(ModuleXmlArtifact that) {
        return Objects.equals(this.groupId, that.groupId)
                && Objects.equals(this.artifactId, that.artifactId)
                && Objects.equals(this.classifier, that.classifier);
    }

    public String getVersion() {
        return version;
    }

    public ModuleXmlArtifact withVersion(String version) {
        return new ModuleXmlArtifact(groupId, artifactId, version, classifier);
    }
}
