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
package org.wildfly.swarm.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BomBuilder {

    public static final String SWARM_GROUP = "org.wildfly.swarm";

    public static String generateBOM(final String template,
                                     final Map<String, String> versions,
                                     final Map<String, List<ExposedComponent>> components) {
        return template.replace("#{dependencies}",
                                String.join("\n", dependenciesList(versions, components).stream()
                                        .map(BomBuilder::pomGav)
                                        .collect(Collectors.toList())));
    }

    public static List<String> dependenciesList(final Map<String, String> versions,
                                                final Map<String, List<ExposedComponent>> components) {
        return versions.keySet().stream()
                .flatMap(module -> components.get(module).stream()
                        .filter(d -> d.bom)
                        .map(d -> gav(d.name, versions.get(module))))
                .collect(Collectors.toList());

    }

    private static String gav(final String name, final String version) {
        return String.format("%s:%s:%s", SWARM_GROUP, name, version);
    }

    private static String pomGav(final String gav) {
        final String[] parts = gav.split(":");

        return String.format(DEP_TEMPLATE, parts[0], parts[1], parts[2]);
    }

    static final private String DEP_TEMPLATE = "      <dependency>\n        <groupId>%s</groupId>\n" +
            "        <artifactId>%s</artifactId>\n        <version>%s</version>\n      </dependency>";
}

