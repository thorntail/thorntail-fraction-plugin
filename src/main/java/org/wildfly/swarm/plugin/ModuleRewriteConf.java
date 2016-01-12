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

    private static final String MODULE = "module:";
    private static final String OPTIONAL = "optional:";

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

            int lineNumber = 0;

            while ((line = in.readLine()) != null) {
                ++lineNumber;
                line = line.trim();
                if ( line.isEmpty() ) {
                    continue;
                }
                if (line.startsWith(OPTIONAL)) {
                    String name = null;
                    String slot = "main";

                    String[] parts = line.substring(OPTIONAL.length()).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current.makeOptional(name, slot);
                } else if ( line.startsWith( MODULE ) ){
                    String name = null;
                    String slot = "main";

                    String[] parts = line.substring( MODULE.length() ).trim().split(":");
                    name = parts[0];
                    if (parts.length > 1) {
                        slot = parts[1];
                    }

                    current = rules.get(name + ":" + slot);
                    if (current == null) {
                        current = new ModuleRewriteRules(name, slot);
                        this.rules.put(name + ":" + slot, current);
                    }
                } else {
                    System.err.println( lineNumber + ":Lines should blank, or start with " + MODULE + " or " + OPTIONAL + ": " + line  );
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
        if (rules != null) {
            desc = rules.rewrite(desc);
        }

        ModuleRewriteRules all = this.rules.get( "ALL:ALL" );

        if ( all != null ) {
            desc = all.rewrite(desc);
        }

        return desc;


    }
}
