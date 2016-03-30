package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/**
 * Generate a Jandex index for classes compiled as part of the current project.
 *
 * @author Heiko Braun
 */
public class Jandexer {

    public static final String INDEX_NAME = "swarm-jandex.idx";

    public Jandexer(Log log, File classesDir) {
        this.log = log;
        this.classesDir = classesDir;
    }

    public void execute() throws IOException {
        final FileSet fs = new FileSet();
        fs.setDirectory(classesDir);
        fs.setIncludes(Collections.singletonList("**/*.class"));

        final Indexer indexer = new Indexer();
        final File dir = fs.getDirectory();
        if (!dir.exists()) {
            return;
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
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(dir, file));

                    final ClassInfo info = indexer.index(fis);
                } finally {
                    IOUtil.close(fis);
                }
            }
        }

        final File idx = new File(dir, "META-INF/" + INDEX_NAME);
        idx.getParentFile()
                .mkdirs();

        FileOutputStream indexOut = null;
        try {
            indexOut = new FileOutputStream(idx);
            final IndexWriter writer = new IndexWriter(indexOut);
            final Index index = indexer.complete();
            writer.write(index);
        } finally {
            IOUtil.close(indexOut);
        }
    }

    private Log log;

    /**
     * By default, process the classes compiled for the project. If you need to process other sets of classes, such as
     * test classes, see the "fileSets" parameter.
     */
    private File classesDir;

}

