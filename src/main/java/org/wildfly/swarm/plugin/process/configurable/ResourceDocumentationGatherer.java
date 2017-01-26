package org.wildfly.swarm.plugin.process.configurable;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;

/**
 * @author Bob McWhirter
 */
public class ResourceDocumentationGatherer extends DocumentationGatherer {

    private final ClassInfo resourceClassInfo;

    private final String name;

    private boolean isRootFraction;

    private static final Set<String> IGNORABLE_FIELDS = new HashSet<String>() {{
        add("pcs");
        add("key");
    }};

    public ResourceDocumentationGatherer(Log log, DocumentationRegistry registry, IndexView index, ClassInfo resourceClassInfo) {
        this(log, nameFor(resourceClassInfo), registry, index, resourceClassInfo);
        this.isRootFraction = true;
    }

    public ResourceDocumentationGatherer(Log log, String name, DocumentationRegistry registry, IndexView index, ClassInfo resourceClassInfo) {
        super(log, registry, index);
        this.resourceClassInfo = resourceClassInfo;
        this.name = name;
        this.isRootFraction = false;
    }

    @Override
    public void gather() {
        process(this.resourceClassInfo, this.isRootFraction);
    }

    protected void process(ClassInfo current, boolean isRootFraction) {
        List<FieldInfo> fields = current.fields();

        for (FieldInfo field : fields) {
            process(field, isRootFraction);
        }

        ClassInfo superClass = getClassByName(current.superName());//index.getClassByName(current.superName());

        if (superClass != null) {
            process(superClass, false);
        }
    }

    protected void process(FieldInfo field, boolean isRootFraction) {
        if (Modifier.isStatic(field.flags())) {
            return;
        }
        if (isSubresources(field)) {
            ClassInfo subresourceInfo = getClassByName(field.type().name());
            new SubresourcesDocumentationGatherer(getLog(), this.registry, this.index, this.name, subresourceInfo).gather();
        } else {
            if (IGNORABLE_FIELDS.contains(field.name())) {
                return;
            }
            if (isRootFraction) {
                String name = this.name + "." + dashize(field.name());
                String docs = "(not yet documented)";
                if (isMarkedAsConfigurable(field)) {
                    name = nameFor(field);
                }
                if (isMarkedAsDocumented(field)) {
                    docs = getDocumentation(field);
                } else {
                    getLog().warn("Missing @AttributeDocumentation: " + this.resourceClassInfo.name() + "#" + field.name());

                }
                addDocumentation(name, docs);
            } else if (isMarkedAsDocumented(field)) {
                addDocumentation(this.name + "." + dashize(field.name()), getDocumentation(field));
            }
        }
    }
}
