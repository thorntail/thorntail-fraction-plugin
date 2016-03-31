package org.wildfly.swarm.plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Bob McWhirter
 */
public class ProvidedDependenciesGenerator {

    public ProvidedDependenciesGenerator(Log log,
                                         MavenProject project) {

        this.log = log;
        this.project = project;
    }

    public void execute() throws ParserConfigurationException, IOException, SAXException {
        if ( hasProvidedDependenciesTxt() ) {
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(this.project.getFile());
        NodeList dependencies = document.getDocumentElement().getElementsByTagName("dependency");

        int num = dependencies.getLength();
        for (int i = 0; i < num; ++i) {
            Element each = (Element) dependencies.item(i);

            NamedNodeMap attrs = each.getAttributes();
            Node scope = attrs.getNamedItemNS("http://wildfly-swarm.io/", "scope");

            if ( scope != null ) {
                addProvidedDependency( each );
            }
        }

        if ( ! this.deps.isEmpty() ) {
            writeProvidedDependenciesTxt();
        }
    }

    protected boolean hasProvidedDependenciesTxt() {
        List<Resource> resources = this.project.getResources();

        for (Resource resource : resources) {
            Path dir = Paths.get(resource.getDirectory());

            Path test = dir.resolve( "provided-dependencies.txt" );

            if (Files.exists( test ) ) {
                return true;
            }
        }

        return false;
    }

    protected void addProvidedDependency(Element node) {
        String groupId = node.getElementsByTagName("groupId").item(0).getTextContent();
        String artifactId = node.getElementsByTagName("artifactId").item(0).getTextContent();
        this.deps.add( new Dep( groupId, artifactId ) );
    }

    protected void writeProvidedDependenciesTxt() throws IOException {
        try ( FileWriter writer = new FileWriter( new File( this.project.getBuild().getOutputDirectory(), "provided-dependencies.txt" ) ) ) {

            for (Dep dep : this.deps) {
                writer.write( dep.groupId + ":" + dep.artifactId + "\n" );
            }

            writer.flush();
        }
    }

    private final Log log;

    private final MavenProject project;

    private final List<Dep> deps = new ArrayList<>();

    private static class Dep {

        public final String groupId;
        public final String artifactId;

        Dep(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

    }

}
