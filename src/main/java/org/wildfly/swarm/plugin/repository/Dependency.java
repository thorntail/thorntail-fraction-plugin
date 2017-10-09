/*
 * Copyright 2017 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.plugin.repository;

import java.util.Objects;

/**
 *
 * @see LicenseMojo
 */
class Dependency {

    // Also exclude all transitive dependencies
    private static final String POM_FORMAT = "<dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions><type>%s</type></dependency>";

    String groupId;

    String artifactId;

    String version;

    String packaging = "jar";

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Dependency that = (Dependency) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return groupId + ':' + artifactId + ':' + packaging + ':' + version;
    }

    String asPomElement() {
        return String.format(POM_FORMAT, groupId, artifactId, version, packaging);
    }

    boolean isComplete() {
        return groupId != null && artifactId != null && version != null && packaging != null;
    }

    String groupArtifactType() {
        return groupId + ':' + artifactId + ':' + packaging;
    }

    boolean hasHigherPriority(Dependency than) {
        // Productized artifacts take precedence
        int result = Boolean.compare(LicenseMojo.isProductizedArtifact(version), LicenseMojo.isProductizedArtifact(than.version));
        if (result == 0) {
            // Try to identify the latest version (major/minor/bugfix)
            String[] d1 = version.split("\\.");
            String[] d2 = than.version.split("\\.");
            result = compareVersion(0, d1, d2);
            if (result == 0) {
                result = compareVersion(1, d1, d2);
                if (result == 0) {
                    result = compareVersion(2, d1, d2);
                }
            }
        }
        return result > 0;
    }

    private int compareVersion(int idx, String[] d1, String[] d2) {
        int d1v = d1.length > idx ? parseVersion(d1[idx]) : -1;
        int d2v = d2.length > idx ? parseVersion(d2[idx]) : -1;
        return Integer.compare(d1v, d2v);
    }

    private int parseVersion(String version) {
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}