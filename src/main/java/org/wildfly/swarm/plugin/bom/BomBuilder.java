/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.plugin.bom;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.DependencyMetadata;

class BomBuilder {

    private BomBuilder() {
    }

    static String generateBOM(final MavenProject rootProject,
                                     final String template,
                                     final Collection<DependencyMetadata> bomItems) {

        String removeIfRegexp = "(?s)#\\{remove-if-" + Pattern.quote(rootProject.getArtifactId()) + "}.*?#\\{/remove-if-"
                + Pattern.quote(rootProject.getArtifactId()) + "}\n?";

        return template.replace("#{dependencies}",
                                String.join("\n",
                                            bomItems.stream()
                                                    .map(BomBuilder::pomGav)
                                                    .collect(Collectors.toList())))
                .replace("#{bom-artifactId}", rootProject.getArtifactId())
                .replace("#{bom-name}", rootProject.getName())
                .replace("#{bom-description}", rootProject.getDescription())
                .replaceAll(removeIfRegexp, "")
                .replaceAll("#\\{/?remove-if-.*?}\n?", "");

    }

    private static String pomGav(DependencyMetadata project) {
        return pomGav(project.getGroupId(), project.getArtifactId(), project.getVersion());
    }

    private static String pomGav(final String groupId, final String artifactId, final String version) {
        return String.format(DEP_TEMPLATE, groupId, artifactId, version);
    }

    private static final String DEP_TEMPLATE = "      <dependency>\n        <groupId>%s</groupId>\n" +
            "        <artifactId>%s</artifactId>\n        <version>%s</version>\n      </dependency>";
}

