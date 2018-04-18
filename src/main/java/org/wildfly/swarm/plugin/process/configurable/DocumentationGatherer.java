package org.wildfly.swarm.plugin.process.configurable;

import java.util.Collection;

import org.apache.maven.plugin.logging.Log;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.wildfly.swarm.plugin.process.ConfigurableDocumentationGenerator;

/**
 * @author Bob McWhirter
 */
public abstract class DocumentationGatherer {

    protected final DocumentationRegistry registry;

    protected final IndexView index;

    private final Log log;

    public DocumentationGatherer(Log log, DocumentationRegistry registry, IndexView index) {
        this.log = log;
        this.registry = registry;
        this.index = index;
    }

    protected Log getLog() {
        return this.log;
    }

    protected void addDocumentation(String key, String docs) {
        this.registry.add(key, docs);
    }

    protected ClassInfo getClassByName(DotName name) {
        return this.index.getClassByName(name);
    }

    protected static String dashize(String name) {
        if (name.toUpperCase().equals(name)) {
            // if it's all uppercase (IO, JCA, JPA) then don't dashize; only
            // dashize if mixed-case.
            return name;
        }

        int numChars = name.length();

        StringBuilder str = new StringBuilder();

        boolean previousWasUppercase = false;

        for (int i = 0; i < numChars; ++i) {
            char c = name.charAt(i);

            if (i == 0) {
                str.append(c);
                previousWasUppercase = true;
            } else if (Character.isUpperCase(c)) {
                if (!previousWasUppercase) {
                    str.append("-");
                    str.append(Character.toLowerCase(c));
                    previousWasUppercase = true;
                } else {
                    str.append(c);
                }
            } else {
                str.append(c);
                previousWasUppercase = false;
            }
        }

        return str.toString();
    }

    protected static boolean isMarkedAsConfigurable(FieldInfo field) {
        for (AnnotationInstance anno : field.annotations()) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.CONFIGURABLE_ANNOTATION) || anno.name().equals(ConfigurableDocumentationGenerator.CONFIGURABLES_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isMarkedAsDocumented(FieldInfo field) {
        for (AnnotationInstance anno : field.annotations()) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.ATTRIBUTE_DOCUMENTATION_ANNOTATION)) {
                return true;
            }
            if (anno.name().equals(ConfigurableDocumentationGenerator.RESOURCE_DOCUMENTATION_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isSingletonResource(FieldInfo field) {
        for (AnnotationInstance anno : field.annotations()) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.SINGLETON_RESOURCE_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isSubresources(FieldInfo field) {
        return field.name().equals("subresources");
    }

    protected static String getDocumentation(FieldInfo field) {
        for (AnnotationInstance anno : field.annotations()) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.ATTRIBUTE_DOCUMENTATION_ANNOTATION)) {
                return anno.value().asString();
            }
            if (anno.name().equals(ConfigurableDocumentationGenerator.RESOURCE_DOCUMENTATION_ANNOTATION)) {
                return anno.value().asString();
            }
        }
        return "";
    }

    protected static String nameFor(FieldInfo field) {
        String prefix = nameFor(field.declaringClass());
        for (AnnotationInstance anno : field.annotations()) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.CONFIGURABLE_ANNOTATION)) {
                String name = getName(prefix, anno);
                if (name != null) {
                    return name;
                }
            } else if (anno.name().equals(ConfigurableDocumentationGenerator.CONFIGURABLES_ANNOTATION)) {
                for (AnnotationInstance nested : anno.value().asNestedArray()) {
                    String name = getName(prefix, nested);
                    if (name != null) {
                        // Only the first annotation declared is used for docs
                        return name;
                    }
                }
            }
        }
        return prefix + "." + field.name();
    }

    private static String getName(String prefix, AnnotationInstance configurable) {
        if (!configurable.value().asString().isEmpty()) {
            return configurable.value().asString();
        } else if (!configurable.value("simpleName").asString().isEmpty()) {
            return prefix + "." + configurable.value("simpleName").asString();
        }
        return null;
    }

    protected static String nameFor(ClassInfo fraction) {
        Collection<AnnotationInstance> annos = fraction.classAnnotations();

        for (AnnotationInstance anno : annos) {
            if (anno.name().equals(ConfigurableDocumentationGenerator.CONFIGURABLE_ANNOTATION)) {
                if (anno.value() != null) {
                    return anno.value().asString();
                }
            }
        }

        String name = fraction.name().local();
        return "swarm." + dashize(name.replace("Fraction", "")).toLowerCase();
    }

    public abstract void gather();
}


