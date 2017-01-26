package org.wildfly.swarm.plugin.process.configurable;

import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;

/**
 * @author Bob McWhirter
 */
public class SubresourcesDocumentationGatherer extends DocumentationGatherer {

    private final String name;

    private final ClassInfo subresourceInfo;


    public SubresourcesDocumentationGatherer(Log log, DocumentationRegistry registry, IndexView index, String name, ClassInfo subresourceInfo) {
        super(log, registry, index);
        this.name = name;
        this.subresourceInfo = subresourceInfo;
    }

    @Override
    public void gather() {
        List<FieldInfo> fields = this.subresourceInfo.fields();

        for (FieldInfo field : fields) {
            if (isMarkedAsDocumented(field)) {
                String docs = getDocumentation(field);

                String name = this.name + "." + dashize(field.name());

                ClassInfo nextClassInfo = null;

                if (!isSingletonResource(field)) {
                    name = name + ".*";
                    nextClassInfo = getClassByName(field.type().asParameterizedType().arguments().get(0).name());
                } else {
                    nextClassInfo = getClassByName(field.type().name());
                }


                new ResourceDocumentationGatherer(getLog(), name, this.registry, this.index, nextClassInfo).gather();
            }
        }
    }
}
