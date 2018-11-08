package org.wildfly.swarm.plugin.utils;

import org.eclipse.aether.artifact.Artifact;
import org.jboss.shrinkwrap.descriptor.api.jbossmodule13.ModuleDescriptor;
import org.jboss.shrinkwrap.descriptor.impl.jbossmodule13.ModuleDescriptorImpl;
import org.jboss.shrinkwrap.descriptor.spi.node.Node;

import java.util.Map;

/**
 * The JBoss Modules {@code module.xml} descriptor in ShrinkWrap Descriptors always forces a specific XML namespace.
 * This means that module.xml files read into the {@code ModuleDescriptor} and then written back to a file will all
 * have the same XML namespace. However, JBoss Modules use XML namespaces for versioning. This subclass makes sure
 * that a XML namespace is only forced when it isn't already present in the XML document. It's not necessary to use
 * this class in a read-only scenario.
 */
public class NamespacePreservingModuleDescriptor extends ModuleDescriptorImpl {
    public NamespacePreservingModuleDescriptor(String descriptorName, Node node) {
        super(descriptorName, node);
    }

    @Override
    public ModuleDescriptor addDefaultNamespaces() {
        if (getRootNode().getAttribute("xmlns") == null) {
            super.addDefaultNamespaces();
        }
        return this;
    }

    // ---

    public void fillVersionAttribute(Map<String, Artifact> artifacts) {
        String moduleVersion = getRootNode().getAttribute("version");
        if (moduleVersion != null) {
            if (moduleVersion.startsWith("${")) {
                moduleVersion = moduleVersion.substring(2, moduleVersion.length() - 1);

                Artifact artifact = artifacts.get(moduleVersion);
                if (artifact != null) {
                    getRootNode().attribute("version", artifact.getVersion());
                }
            }
        }
    }
}
