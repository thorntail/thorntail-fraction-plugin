package org.wildfly.swarm.plugin.process.configurable;

import org.apache.maven.plugin.logging.Log;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;

/**
 * @author Bob McWhirter
 */
public class AnnotationDocumentationGatherer extends DocumentationGatherer {

    private final AnnotationInstance anno;

    public AnnotationDocumentationGatherer(Log log, DocumentationRegistry documentationRegistry, IndexView totalIndex, AnnotationInstance anno) {
        super(log, documentationRegistry, totalIndex);
        this.anno = anno;
    }

    @Override
    public void gather() {
        String name = this.anno.value().asString();
        FieldInfo target = this.anno.target().asField();

        String docs = "(not yet documented)";
        if (isMarkedAsDocumented(target)) {
            docs = getDocumentation(this.anno.target().asField());
        } else {
            getLog().warn("Missing @AttributeDocumentation: " + target.declaringClass().name() + "#" + target.name());
        }

        addDocumentation(name, docs);
    }
}
