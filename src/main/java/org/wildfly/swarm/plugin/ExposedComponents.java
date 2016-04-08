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

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ExposedComponents {
    public static ExposedComponents parseDescriptor(final String version, final URL content) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            final TypeFactory typeFactory = mapper.getTypeFactory();
            final Map<String, List<ExposedComponent>> components =
                    mapper.readValue(content,
                                     typeFactory.constructMapType(Map.class,
                                                                  typeFactory.constructType(String.class),
                                                                  typeFactory.constructCollectionType(List.class, ExposedComponent.class)));
            final String moduleName = components.keySet().stream().findFirst().orElse(null);

            return new ExposedComponents(moduleName, version, components.get(moduleName));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse descriptor", e);
        }
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<ExposedComponent> components() {
        return components;
    }

    private ExposedComponents(final String name, final String version, final List<ExposedComponent> components) {
        this.name = name;
        this.version = version;
        this.components = components;
    }

    final private String name;
    final private String version;
    final private List<ExposedComponent> components;
}
