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

import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.DependenciesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDependencyType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;

/**
 * @author Bob McWhirter
 */
public class ModuleRewriteRules {


    public abstract static class Rule {
        public abstract void rewrite(ModuleDescriptor desc);
    }

    public static class Optional extends Rule {
        private String name;

        private String slot;

        public Optional(String name, String slot) {
            this.name = name;
            this.slot = slot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            List<ModuleDependencyType<DependenciesType<ModuleDescriptor>>> deps = desc.getOrCreateDependencies().getAllModule();
            for (ModuleDependencyType<DependenciesType<ModuleDescriptor>> each : deps) {
                String depName = each.getName();
                String depSlot = each.getSlot();

                if (depSlot == null) {
                    depSlot = "main";
                }

                if (depName.equals(this.name) && depSlot.equals(this.slot)) {
                    each.optional(true);
                }
            }
        }
    }

    private final String name;

    private final String slot;

    private List<Rule> rules = new ArrayList<>();

    public ModuleRewriteRules(String name, String slot) {
        this.name = name;
        this.slot = slot;
    }

    public void makeOptional(String name, String slot) {
        this.rules.add(new Optional(name, slot));
    }

    public ModuleDescriptor rewrite(ModuleDescriptor desc) {
        for (Rule rule : this.rules) {
            rule.rewrite(desc);
        }

        return desc;
    }
}
