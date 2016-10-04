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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.DependenciesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDependencyType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;

/**
 * @author Bob McWhirter
 */
public class ModuleRewriteRules {


    public ModuleRewriteRules(String name, String slot) {
        this.name = name;
        this.slot = slot;
    }

    public void makeOptional(String name, String slot) {
        this.rules.add(new Optional(name, slot));
    }

    public void include(String name, String slot) {
        this.rules.add(new Include(name, slot));
    }

    public void replace(String origName, String origSlot, String replaceName, String replaceSlot) {
        System.err.println("adding replace: " + origName + ":" + origSlot + " with " + replaceName + ":" + replaceSlot);
        this.rules.add(new Replace(origName, origSlot, replaceName, replaceSlot));
    }

    public ModuleDescriptor rewrite(ModuleDescriptor desc) {
        for (Rule rule : this.rules) {
            rule.rewrite(desc);
        }

        return desc;
    }

    private final String name;

    private final String slot;

    private List<Rule> rules = new ArrayList<>();

    public abstract static class Rule {
        public abstract void rewrite(ModuleDescriptor desc);
    }

    public static class Include extends Rule {
        public Include(String name, String slot) {
            this.name = name;
            this.slot = slot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            DependenciesType<ModuleDescriptor> dependencies = desc.getOrCreateDependencies();
            // If module dependency already exists, ignore
            if (dependencies.getAllModule().stream()
                    .filter(d -> name.equals(d.getName()))
                    .filter(d -> Objects.equals(slot, d.getSlot()))
                    .count() == 0L) {
                dependencies.createModule().name(name).slot(slot == null ? "main" : slot);
            }
        }

        private final String name;

        private final String slot;
    }


    public static class Optional extends Rule {
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

        private final String name;

        private final String slot;
    }

    public static class Replace extends Rule {
        private final String origName;

        private final String origSlot;

        private final String replaceName;

        private final String replaceSlot;

        public Replace(String origName, String origSlot, String replaceName, String replaceSlot) {
            this.origName = origName;
            this.origSlot = origSlot;

            this.replaceName = replaceName;
            this.replaceSlot = replaceSlot;

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

                if (depName.equals(this.origName) && depSlot.equals(this.origSlot)) {
                    each.name(this.replaceName).slot(this.replaceSlot);
                }
            }

        }
    }
}
