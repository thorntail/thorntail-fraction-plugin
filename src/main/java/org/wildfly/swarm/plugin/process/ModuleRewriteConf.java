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
package org.wildfly.swarm.plugin.process;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;

/**
 * @author Bob McWhirter
 */
public class ModuleRewriteConf {

    public ModuleRewriteConf(Path file) throws IOException {
        if (Files.exists(file)) {
            load(file);
        }
    }

    public ModuleDescriptor rewrite(ModuleDescriptor desc) {
        String descName = desc.getName();
        String descSlot = desc.getSlot();

        if (descSlot == null) {
            descSlot = "main";
        }

        ModuleRewriteRules rules = this.rules.get(descName + ":" + descSlot);
        if (rules != null) {
            desc = rules.rewrite(desc);
        }

        ModuleRewriteRules all = this.rules.get("ALL:ALL");

        if (all != null) {
            desc = all.rewrite(desc);
        }

        return desc;


    }

    protected void load(Path file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file.toFile()))) {

            ModuleRewriteRules current = null;

            String line = null;

            int lineNumber = 0;

            while ((line = in.readLine()) != null) {
                ++lineNumber;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith(REPLACE)) {
                    String[] chunks = line.substring(REPLACE.length()).trim().split(">");

                    String origName = null;
                    String origSlot = "main";

                    String[] origParts = chunks[0].trim().split(":");
                    origName = origParts[0];
                    if (origParts.length > 1) {
                        origSlot = origParts[1];
                    }

                    String replaceName = null;
                    String replaceSlot = "main";

                    String[] replaceParts = chunks[1].trim().split(":");
                    replaceName = replaceParts[0];
                    if (replaceParts.length > 1) {
                        replaceSlot = replaceParts[1];
                    }

                    current.replace(origName, origSlot, replaceName, replaceSlot);
                } else if (line.startsWith(OPTIONAL)) {
                    String name = null;
                    String slot = "main";

                    String[] parts = line.substring(OPTIONAL.length()).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current.makeOptional(name, slot);
                } else if (line.startsWith(MODULE)) {
                    String name = null;
                    String slot = "main";

                    String[] parts = line.substring(MODULE.length()).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current = rules.get(name + ":" + slot);
                    if (current == null) {
                        current = new ModuleRewriteRules(name, slot);
                        this.rules.put(name + ":" + slot, current);
                    }
                } else if (line.startsWith(INCLUDE)) {
                    String name = null;
                    String slot = null;

                    String[] parts = line.substring(INCLUDE.length()).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current.include(name, slot);
                } else {
                    System.err.println(lineNumber + ":Lines should be blank or start with " + MODULE + ", " + INCLUDE + " or " + OPTIONAL + " - " + line);
                }
            }
        }
    }

    private static final String MODULE = "module:";

    private static final String INCLUDE = "include:";

    private static final String OPTIONAL = "optional:";

    private static final String REPLACE = "replace:";

    private Map<String, ModuleRewriteRules> rules = new HashMap<>();
}
