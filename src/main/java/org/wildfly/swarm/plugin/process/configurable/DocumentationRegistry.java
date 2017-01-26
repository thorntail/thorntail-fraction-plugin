package org.wildfly.swarm.plugin.process.configurable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Bob McWhirter
 */
public class DocumentationRegistry {

    public DocumentationRegistry() {

    }

    public void add(String key, String docs) {
        this.registry.put(key, docs);
    }

    public String toString() {
        return registry.toString();
    }

    public void dump() {
        this.registry.keySet().stream().sorted()
                .forEach(key -> {
                    System.err.println(key + ": " + this.registry.get(key));
                });
    }

    public Properties asProperties() {
        Properties props = new Properties();
        this.registry.keySet().stream().sorted()
                .forEach(key -> {
                    props.setProperty(key, this.registry.get(key));
                });

        return props;
    }

    private Map<String, String> registry = new HashMap<>();
}
