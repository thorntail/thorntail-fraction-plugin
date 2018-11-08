package org.wildfly.swarm.plugin.process;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.wildfly.swarm.plugin.FileSet;
import org.wildfly.swarm.plugin.FractionMetadata;

/**
 * Generate a Jandex index for classes compiled as part of the current project.
 *
 * @author Heiko Braun
 */
public class Jandexer {

    public static final String INDEX_NAME = "jandex.idx";

    public Jandexer(Log log, File classesDir) {
        this.log = log;
        this.classesDir = classesDir;
    }

    public FractionMetadata apply(FractionMetadata meta) throws MojoExecutionException {
        if (!meta.hasJavaCode()) {
            return meta;
        }

        final FileSet fs = new FileSet();
        fs.setDirectory(classesDir);
        fs.setIncludes(Collections.singletonList("**/*.class"));
        fs.setExcludes(Collections.singletonList("**/deployment/*.class"));
        fs.setExcludes(Collections.singletonList("**/detect/*.class"));

        final Indexer indexer = new Indexer();
        final File dir = fs.getDirectory();
        if (!dir.exists()) {
            return meta;
        }

        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(dir);

        if (fs.isUseDefaultExcludes()) {
            scanner.addDefaultExcludes();
        }

        final List<String> includes = fs.getIncludes();
        if (includes != null) {
            scanner.setIncludes(includes.toArray(new String[]{}));
        }

        final List<String> excludes = fs.getExcludes();
        if (excludes != null) {
            scanner.setExcludes(excludes.toArray(new String[]{}));
        }

        scanner.scan();
        final String[] files = scanner.getIncludedFiles();

        for (final String file : files) {
            if (file.endsWith(".class")) {
                try (FileInputStream fis = new FileInputStream(new File(dir, file))) {
                    indexer.index(fis);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed indexing class " + file, e);
                }
            }
        }

        final File idx = new File(dir, "META-INF/" + INDEX_NAME);
        idx.getParentFile().mkdirs();

        try (FileOutputStream indexOut = new FileOutputStream(idx)) {
            final IndexWriter writer = new IndexWriter(indexOut);
            final Index index = indexer.complete();
            writer.write(index);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing Jandex index " + idx, e);
        }
        return meta;
    }

    private Log log;

    /**
     * By default, process the classes compiled for the project. If you need to process other sets of classes, such as
     * test classes, see the "fileSets" parameter.
     */
    private File classesDir;

}
