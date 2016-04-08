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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ExposedComponent {
    public boolean isBom() {
        return bom;
    }

    public String doc() {
        return doc;
    }

    public String name() {
        return name;
    }

    public void setBom(boolean bom) {
        this.bom = bom;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name = null;
    private String doc = null;
    private boolean bom = true;
}
