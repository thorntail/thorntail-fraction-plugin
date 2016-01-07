package org.wildfly.swarm.plugin;

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

    private Map<String, ModuleRewriteRules> rules = new HashMap<>();

    public ModuleRewriteConf(Path file) throws IOException {
        if (Files.exists(file)) {
            load(file);
        }
    }

    protected void load(Path file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file.toFile()))) {

            ModuleRewriteRules current = null;

            String line = null;

            while ((line = in.readLine()) != null) {
                if (line.startsWith("*")) {
                    String name = null;
                    String slot = "main";

                    String[] parts = line.substring(1).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current.makeOptional(name, slot);
                } else {
                    String name = null;
                    String slot = "main";

                    String[] parts = line.trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current = rules.get(name + ":" + slot);
                    if (current == null) {
                        current = new ModuleRewriteRules(name, slot);
                        this.rules.put(name + ":" + slot, current);
                    }
                }
            }
        }
    }

    public ModuleDescriptor rewrite(ModuleDescriptor desc) {
        String descName = desc.getName();
        String descSlot = desc.getSlot();

        if ( descSlot == null ) {
            descSlot = "main";
        }

        ModuleRewriteRules rules = this.rules.get(descName +":" + descSlot );
        if (rules == null) {
            return desc;
        }

        return rules.rewrite(desc);

    }
}
