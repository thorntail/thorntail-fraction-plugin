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
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ArtifactType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.DependenciesType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDependencyType;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ResourcesType;

import static org.wildfly.swarm.plugin.utils.DescriptorUtils.noDependencies;
import static org.wildfly.swarm.plugin.utils.DescriptorUtils.noResources;

/**
 * @author Bob McWhirter
 */
public class ModuleRewriteRules {

    public ModuleRewriteRules() {
    }

    void makeOptional(String name, String slot) {
        this.rules.add(new Optional(name, slot));
    }

    void include(String name, String slot) {
        this.rules.add(new Include(name, slot));
    }

    void export(String name, String slot) {
        this.rules.add(new Export(name, slot));
    }

    void replace(String origName, String origSlot, String replaceName, String replaceSlot) {
        System.err.println("adding replace: " + origName + ":" + origSlot + " with " + replaceName + ":" + replaceSlot);
        this.rules.add(new Replace(origName, origSlot, replaceName, replaceSlot));
    }

    void removeArtifact(String pattern) {
        this.rules.add(new RemoveArtifact(pattern));
    }

    void forceArtifactVersion(ModuleXmlArtifact expectedArtifact, String newVersion) {
        this.rules.add(new ForceArtifactVersion(expectedArtifact, newVersion));
    }

    void replaceArtifact(ModuleXmlArtifact expectedArtifact, ModuleXmlArtifact newArtifact) {
        this.rules.add(new ReplaceArtifact(expectedArtifact, newArtifact));
    }

    ModuleDescriptor rewrite(ModuleDescriptor desc) {
        for (Rule rule : this.rules) {
            rule.rewrite(desc);
        }

        return desc;
    }

    private List<Rule> rules = new ArrayList<>();

    abstract static class Rule {
        public abstract void rewrite(ModuleDescriptor desc);
    }

    private static class Include extends Rule {
        Include(String name, String slot) {
            this.name = name;
            this.slot = slot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noDependencies(desc)) {
                return;
            }

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

    private static class Export extends Rule {
        Export(String name, String slot) {
            this.name = name;
            this.slot = slot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noDependencies(desc)) {
                return;
            }

            DependenciesType<ModuleDescriptor> dependencies = desc.getOrCreateDependencies();
            dependencies.getAllModule().stream()
                    .filter(d -> name.equals(d.getName()))
                    .filter(d -> Objects.equals(slot, d.getSlot()))
                    .findFirst()
                    .ifPresent(d -> d.export(true));
        }

        private final String name;

        private final String slot;
    }

    private static class Optional extends Rule {
        Optional(String name, String slot) {
            this.name = name;
            this.slot = slot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noDependencies(desc)) {
                return;
            }

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

    private static class Replace extends Rule {
        private final String origName;

        private final String origSlot;

        private final String replaceName;

        private final String replaceSlot;

        Replace(String origName, String origSlot, String replaceName, String replaceSlot) {
            this.origName = origName;
            this.origSlot = origSlot;

            this.replaceName = replaceName;
            this.replaceSlot = replaceSlot;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noDependencies(desc)) {
                return;
            }

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

    private static class RemoveArtifact extends Rule {
        private final Pattern pattern;

        RemoveArtifact(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noResources(desc)) {
                return;
            }

            ResourcesType<ModuleDescriptor> resources = desc.getOrCreateResources();
            List<ArtifactType<ResourcesType<ModuleDescriptor>>> artifacts = resources.getAllArtifact();
            resources.removeAllArtifact();
            for (ArtifactType<ResourcesType<ModuleDescriptor>> artifact : artifacts) {
                if (!pattern.matcher(artifact.getName()).find()) {
                    resources.createArtifact().name(artifact.getName());
                }
            }
        }
    }

    private static class ForceArtifactVersion extends Rule {
        private final ModuleXmlArtifact expectedArtifact; // version part is ignored
        private final String newVersion;

        ForceArtifactVersion(ModuleXmlArtifact expectedArtifact, String newVersion) {
            this.expectedArtifact = expectedArtifact;
            this.newVersion = newVersion;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noResources(desc)) {
                return;
            }

            ResourcesType<ModuleDescriptor> resources = desc.getOrCreateResources();
            List<ArtifactType<ResourcesType<ModuleDescriptor>>> artifacts = resources.getAllArtifact();
            for (ArtifactType<ResourcesType<ModuleDescriptor>> artifact : artifacts) {
                ModuleXmlArtifact presentArtifact = ModuleXmlArtifact.parse(artifact.getName());
                if (expectedArtifact.equalsIgnoringVersion(presentArtifact)) {
                    artifact.name(presentArtifact.withVersion(newVersion).toString());
                }
            }
        }
    }

    private static class ReplaceArtifact extends Rule {
        private final ModuleXmlArtifact expectedArtifact; // version part is ignored
        private final ModuleXmlArtifact newArtifact;

        ReplaceArtifact(ModuleXmlArtifact expectedArtifact, ModuleXmlArtifact newArtifact) {
            this.expectedArtifact = expectedArtifact;
            this.newArtifact = newArtifact;
        }

        @Override
        public void rewrite(ModuleDescriptor desc) {
            if (noResources(desc)) {
                return;
            }

            ResourcesType<ModuleDescriptor> resources = desc.getOrCreateResources();
            List<ArtifactType<ResourcesType<ModuleDescriptor>>> artifacts = resources.getAllArtifact();
            for (ArtifactType<ResourcesType<ModuleDescriptor>> artifact : artifacts) {
                ModuleXmlArtifact presentArtifact = ModuleXmlArtifact.parse(artifact.getName());
                if (expectedArtifact.equalsIgnoringVersion(presentArtifact)) {
                    artifact.name(newArtifact.toString());
                }
            }
        }
    }
}
