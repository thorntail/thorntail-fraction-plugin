package org.wildfly.swarm.plugin.utils;

import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.descriptor.spi.node.Node;
import org.jboss.shrinkwrap.descriptor.spi.node.NodeDescriptor;

public class DescriptorUtils {
    private DescriptorUtils() {
    }

    public static boolean noDependencies(Descriptor desc) {
        return noChildPresent(desc, "dependencies");
    }

    public static boolean noResources(Descriptor desc) {
        return noChildPresent(desc, "resources");
    }

    private static boolean noChildPresent(Descriptor desc, String childName) {
        if (desc instanceof NodeDescriptor) {
            Node node = ((NodeDescriptor) desc).getRootNode();
            return node.get(childName).isEmpty();
        }
        return false;
    }

}
