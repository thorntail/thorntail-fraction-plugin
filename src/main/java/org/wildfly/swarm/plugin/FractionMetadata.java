/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Bob McWhirter
 */
public class FractionMetadata extends DependencyMetadata {

    public FractionMetadata(String groupId, String artifactId, String version) {
        super(groupId, artifactId, version, null, "jar");
    }

    @JsonIgnore
    public boolean isFraction() {
        if (!this.tags.isEmpty()
                || hasModuleConf()
                || isInternal()
                || isBootstrap()
                || this.stabilityIndex != null
                || hasJavaFraction() ) {
            return true;
        }

        return false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    @JsonIgnore
    public List<String> getTags() {
        return tags;
    }

    @JsonProperty("tags")
    public String getTagsString() {
        if ( this.tags.isEmpty() ) {
            return "";
        }

        return String.join( ",", this.tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean isInternal() {
        return internal != null && internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    @JsonIgnore
    public boolean isBootstrap() {
        return bootstrap != null;
    }

    public void setBootstrap(String bootstrap) {
        this.bootstrap = bootstrap;
    }

    @JsonIgnore
    public String getBootstrap() {
        return this.bootstrap;
    }

    @JsonIgnore
    public boolean hasModuleConf() {
        return this.moduleConf != null;
    }

    public void setModuleConf(Path moduleConf) {
        this.moduleConf = moduleConf;
    }

    @JsonIgnore
    public Path getModuleConf() {
        return this.moduleConf;
    }

    public void setHasJavaCode(boolean hasJavaCode) {
        this.hasJavaCode = hasJavaCode;
    }

    @JsonIgnore
    public boolean hasJavaCode() {
        return this.hasJavaCode;
    }

    public void setJavaFraction(Path javaFraction) {
        if (javaFraction != null) {
            this.javaFraction = javaFraction;
            setHasJavaCode(true);
        }
    }

    @JsonIgnore
    public Path getJavaFraction() {
        return this.javaFraction;
    }

    @JsonIgnore
    public boolean hasJavaFraction() {
        return this.javaFraction != null;
    }

    public void setBaseModulePath(Path baseModulePath) {
        this.baseModulePath = baseModulePath;
    }

    @JsonIgnore
    public Path getBaseModulePath() {
        return this.baseModulePath;
    }

    @JsonIgnore
    public String getModule() {
        if ( this.bootstrap != null ) {
            if ( this.bootstrap.equals( "false" ) ) {
                return null;
            }
            return this.bootstrap;
        }
        return this.baseModulePath.toString().replace(File.separatorChar, '.' );
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStabilityIndex(StabilityLevel stabilityIndex) {
        this.stabilityIndex = stabilityIndex;
    }

    @JsonIgnore
    public StabilityLevel getStabilityIndex() {
        if ( this.stabilityIndex == null ) {
            return StabilityLevel.UNSTABLE;
        }
        return this.stabilityIndex;
    }

    @JsonProperty("stabilityIndex")
    public int jsonStabilityIndex() {
        return getStabilityIndex().ordinal();
    }

    @JsonProperty("stabilityDescription")
    public String jsonStabilityDescription() {
        return getStabilityIndex().toString().toLowerCase();
    }

    public void addDependency(DependencyMetadata dependency) {
        this.dependencies.add(dependency);
    }

    @JsonIgnore
    public Set<DependencyMetadata> getDependencies() {
        return this.dependencies;
    }

    public Set<FractionMetadata> getFractionDependencies() {
        return this.dependencies
                .stream()
                .map(FractionRegistry.INSTANCE::of)
                .filter(e -> e != null)
                .collect(Collectors.toSet());
    }

    public String toString() {
        return getGroupId() + ":" + getArtifactId() + ":" + this.getVersion();
    }

    @JsonIgnore
    public String getDependenciesString() {
        return String.join(", ", getFractionDependencies().stream().map(e -> e.toString())
                .collect(Collectors.toList()));
    }

    private String name;

    private String description;

    private List<String> tags = new ArrayList<>();

    private Boolean internal;

    private String bootstrap;

    private Path moduleConf;

    private Path baseModulePath;

    private Path javaFraction;

    private boolean hasJavaCode;

    private final Set<DependencyMetadata> dependencies = new HashSet<>();

    // 2 = Unstable
    private StabilityLevel stabilityIndex = null;
}
