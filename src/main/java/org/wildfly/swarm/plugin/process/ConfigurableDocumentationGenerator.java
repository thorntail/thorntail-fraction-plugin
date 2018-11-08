package org.wildfly.swarm.plugin.process;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.process.configurable.AnnotationDocumentationGatherer;
import org.wildfly.swarm.plugin.process.configurable.DocumentationRegistry;
import org.wildfly.swarm.plugin.process.configurable.ResourceDocumentationGatherer;

/**
 * @author Bob McWhirter
 */
public class ConfigurableDocumentationGenerator {
    public static final DotName FRACTION_CLASS = DotName.createSimple("org.wildfly.swarm.spi.api.Fraction");

    public static final DotName CONFIGURABLE_ANNOTATION = DotName.createSimple("org.wildfly.swarm.spi.api.annotations.Configurable");

    public static final DotName CONFIGURABLES_ANNOTATION = DotName.createSimple("org.wildfly.swarm.spi.api.annotations.Configurables");

    public static final DotName SINGLETON_RESOURCE_ANNOTATION = DotName.createSimple("org.wildfly.swarm.config.runtime.SingletonResource");

    public static final DotName ATTRIBUTE_DOCUMENTATION_ANNOTATION = DotName.createSimple("org.wildfly.swarm.config.runtime.AttributeDocumentation");

    public static final DotName RESOURCE_DOCUMENTATION_ANNOTATION = DotName.createSimple("org.wildfly.swarm.config.runtime.ResourceDocumentation");

    private final Path classesDir;

    private final MavenProject project;

    private final DocumentationRegistry documentationRegistry;

    private final Log log;

    public ConfigurableDocumentationGenerator(Log log, MavenProject project, File classesDir) {
        this.log = log;
        this.project = project;
        this.classesDir = classesDir.toPath();
        this.documentationRegistry = new DocumentationRegistry();
    }

    public FractionMetadata apply(FractionMetadata meta) throws MojoExecutionException {
        if (!meta.hasJavaCode()) {
            return meta;
        }

        if (!Files.exists(this.classesDir)) {
            return meta;
        }

        final Path idx = this.classesDir.resolve("META-INF").resolve(Jandexer.INDEX_NAME);

        if (!Files.exists(idx)) {
            return meta;
        }

        IndexView ownIndex = null;
        IndexView totalIndex = null;
        try {
            ownIndex = loadOwnIndex();
            totalIndex = buildIndex(ownIndex);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed creating Jandex indexes", e);
        }

        process(ownIndex, totalIndex);

        //this.documentationRegistry.dump();
        Properties props = this.documentationRegistry.asProperties();
        props.setProperty("fraction", meta.getName().toLowerCase());

        Path docs = this.classesDir.resolve("META-INF").resolve("configuration-meta.properties");

        try {
            Files.createDirectories(docs.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (OutputStream out = new FileOutputStream(docs.toFile())) {
            props.store(out, "Created by thorntail-fraction-plugin");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing configuration-meta.properties", e);
        }

        return meta;
    }

    protected IndexView buildIndex(IndexView ownIndex) throws IOException {
        IndexView dependentIndexes = loadDependentIndexes();

        return CompositeIndex.create(ownIndex, dependentIndexes);
    }

    protected IndexView loadOwnIndex() throws IOException {
        final Path idx = this.classesDir.resolve("META-INF").resolve(Jandexer.INDEX_NAME);

        IndexView index = null;

        if (Files.exists(idx)) {
            try (InputStream in = new FileInputStream(idx.toFile())) {
                index = loadIndex(in);
            }
        }

        return index;
    }

    protected IndexView loadDependentIndexes() throws IOException {
        List<IndexView> indexes = new ArrayList<>();
        for (Artifact artifact : this.project.getArtifacts()) {
            IndexView index = loadIndex(artifact);
            if (index != null) {
                indexes.add(index);
            }
        }
        return CompositeIndex.create(indexes);
    }

    protected IndexView loadIndex(Artifact dep) throws IOException {
        if (dep.getFile() == null) {
            return null;
        }

        return loadDependentIndexFromArchive(dep.getFile());
    }

    protected IndexView loadDependentIndexFromArchive(File archive) throws IOException {
        try (JarFile jar = new JarFile(archive)) {

            ZipEntry entry = jar.getEntry("META-INF/" + Jandexer.INDEX_NAME);
            if (entry != null) {
                return loadIndex(jar.getInputStream(entry));

            }
            Indexer indexer = new Indexer();

            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry each = entries.nextElement();
                if (each.getName().endsWith(".class") && !each.getName().equals("module-info.class")) {
                    try (InputStream in = jar.getInputStream(each)) {
                        indexer.index(in);
                    }
                }

            }

            return indexer.complete();
        }
    }

    protected IndexView loadIndex(InputStream in) throws IOException {
        IndexReader reader = new IndexReader(in);
        return reader.read();
    }

    protected void process(IndexView ownIndex, IndexView totalIndex) {
        Collection<ClassInfo> fractions = ownIndex.getAllKnownImplementors(FRACTION_CLASS);

        for (ClassInfo fraction : fractions) {
            new ResourceDocumentationGatherer(this.log, this.documentationRegistry, totalIndex, fraction).gather();
        }

        Collection<AnnotationInstance> annos = ownIndex.getAnnotations(CONFIGURABLE_ANNOTATION);

        for (AnnotationInstance anno : annos) {
            AnnotationTarget annoTarget = anno.target();
            if (annoTarget.kind().equals(AnnotationTarget.Kind.FIELD)) {
                ClassInfo declaringClass = annoTarget.asField().declaringClass();
                if (!fractions.contains(declaringClass)) {
                    new AnnotationDocumentationGatherer(this.log, this.documentationRegistry, totalIndex, anno).gather();
                }
            }
        }
    }
}
