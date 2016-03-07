package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/**
 * Generate a Jandex index for classes compiled as part of the current project.
 * @author Heiko Braun
 */
@org.apache.maven.plugins.annotations.Mojo(
        name = "jandex",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class JandexMojo extends AbstractMojo
{

    /**
     * By default, process the classes compiled for the project. If you need to process other sets of classes, such as
     * test classes, see the "fileSets" parameter.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDir;

    /**
     * Process the classes found in these file-sets, after considering the specified includes and excludes, if any. The
     * format is: <br/>
     *
     * <pre>
     * <code>
     * &lt;fileSets&gt;
     *   &lt;fileSet&gt;
     *     &lt;directory&gt;path-or-expression&lt;/directory&gt;
     *     &ltincludes&gt;
     *       &lt;include&gt;some/thing/*.good&lt;/include&gt;
     *     &lt;includes&gt;
     *     &lt;excludes&gt;
     *       &lt;exclude&gt;some/thing/*.bad&lt;/exclude&gt;
     *     &lt;/excludes&lt;
     *   &lt;/fileSet&gt;
     * &lt;/fileSets&gt;
     * </code>
     * </pre>
     *
     * <br>
     * <em>NOTE: Standard globbing expressions are supported in includes/excludes.</em>
     *
     * @parameter
     */
    @Parameter
    private List<FileSet> fileSets;

    /**
     * If true, construct an implied file-set using the target/classes directory, and process the classes there.
     */
    @Parameter(defaultValue = "true")
    private final boolean processDefaultFileSet = true;

    /**
     * Print verbose output (debug output without needing to enable -X for the whole build)
     *
     * @parameter default-value="false"
     */
    @Parameter(defaultValue = "false")
    private boolean verbose = false;

    /**
     * The name of the index file. Default's to 'swarm-jandex.idx'
     */
    @Parameter(defaultValue = "swarm-jandex.idx")
    private String indexName = "swarm-jandex.idx";

    /**
     * Skip execution if set.
     */
    @Parameter(defaultValue = "false")
    private boolean skip = false;

    private Log log;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().info( "Jandex execution skipped." );
            return;
        }

        if ( processDefaultFileSet )
        {
            boolean found = false;
            if ( fileSets != null )
            {
                for ( final FileSet fileset : fileSets )
                {
                    if ( fileset.getDirectory()
                                .equals( classesDir ) )
                    {
                        found = true;
                    }
                }
            }

            if ( !found )
            {
                final FileSet fs = new FileSet();
                fs.setDirectory( classesDir );
                fs.setIncludes(Collections.singletonList("**/*.class" ) );

                if ( fileSets == null )
                {
                    fileSets = new ArrayList<FileSet>();
                }

                fileSets.add( fs );
            }
        }

        final Indexer indexer = new Indexer();
        for ( final FileSet fileset : fileSets )
        {
            final File dir = fileset.getDirectory();
            if ( !dir.exists() )
            {
                getLog().info( "[SKIP] Cannot process fileset in directory: " + fileset.getDirectory()
                                    + ". Directory does not exist!" );
                continue;
            }

            final DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir( dir );

            if ( fileset.isUseDefaultExcludes() )
            {
                scanner.addDefaultExcludes();
            }

            final List<String> includes = fileset.getIncludes();
            if ( includes != null )
            {
                scanner.setIncludes( includes.toArray( new String[] {} ) );
            }

            final List<String> excludes = fileset.getExcludes();
            if ( excludes != null )
            {
                scanner.setExcludes( excludes.toArray( new String[] {} ) );
            }

            scanner.scan();
            final String[] files = scanner.getIncludedFiles();

            for ( final String file : files )
            {
                if ( file.endsWith( ".class" ) )
                {
                    FileInputStream fis = null;
                    try
                    {
                        fis = new FileInputStream( new File( dir, file ) );

                        final ClassInfo info = indexer.index( fis );
                        if ( doVerbose() && info != null )
                        {
                            getLog().info( "Indexed " + info.name() + " (" + info.annotations()
                                                                                 .size() + " annotations)" );
                        }
                    }
                    catch ( final Exception e )
                    {
                        throw new MojoExecutionException( e.getMessage(), e );
                    }
                    finally
                    {
                        IOUtil.close(fis );
                    }
                }
            }

            final File idx = new File( dir, "META-INF/"+indexName );
            idx.getParentFile()
               .mkdirs();

            FileOutputStream indexOut = null;
            try
            {
                indexOut = new FileOutputStream( idx );
                final IndexWriter writer = new IndexWriter( indexOut );
                final Index index = indexer.complete();
                writer.write( index );
            }
            catch ( final IOException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            finally
            {
                IOUtil.close( indexOut );
            }
        }

    }

    private boolean doVerbose()
    {
        return verbose || getLog().isDebugEnabled();
    }

    public boolean isVerbose()
    {
        return verbose;
    }

    public void setVerbose( final boolean verbose )
    {
        this.verbose = verbose;
    }

    public boolean isSkip()
    {
        return skip;
    }

    public void setSkip( final boolean skip )
    {
        this.skip = skip;
    }

    public void setLog( final Log log )
    {
        this.log = log;
    }

    public Log getLog()
    {
        return log;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }
}

