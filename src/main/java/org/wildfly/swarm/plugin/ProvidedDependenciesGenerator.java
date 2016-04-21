package org.wildfly.swarm.plugin;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.xml.sax.SAXException;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
public class ProvidedDependenciesGenerator {

    public ProvidedDependenciesGenerator(Log log,
                                         DefaultRepositorySystemSession repositorySystemSession,
                                         RepositorySystem repositorySystem,
                                         MavenProject project) {

        this.log = log;
        this.repositorySystemSession = repositorySystemSession;
        this.repositorySystem = repositorySystem;
        this.project = project;
    }

    public void execute() throws ParserConfigurationException, IOException, SAXException, DependencyCollectionException {
        consumeProvidedDependenciesTxt();

        determineProvidedDependencies();

        if (!this.deps.isEmpty()) {
            writeWildflySwarmClasspathConf();
        }
    }

    protected void consumeProvidedDependenciesTxt() throws IOException {
        List<Resource> resources = this.project.getResources();

        for (Resource resource : resources) {
            Path dir = Paths.get(resource.getDirectory());

            Path test = dir.resolve("provided-dependencies.txt");

            if (Files.exists(test)) {
                try (BufferedReader reader = Files.newBufferedReader(test)) {
                    reader.lines().forEach(l -> {
                        int sep = l.indexOf(':');
                        this.deps.add(new Dep(l.substring(0, sep), l.substring(sep + 1)));
                    });
                }

                // Remove provided-dependencies.txt from output
                Files.delete(Paths.get(this.project.getBuild().getOutputDirectory(), "provided-dependencies.txt"));
            }
        }
    }

    protected void determineProvidedDependencies() throws DependencyCollectionException {
        final CollectRequest request = new CollectRequest();

        request.setDependencies(
                this.project.getDependencyArtifacts().stream()
                        .filter(a -> a.getScope().equals("compile"))
                        .filter(a -> !"org.wildfly.swarm".equals(a.getGroupId()))
                        .map(a -> new Dependency(new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getType(), a.getVersion()), "compile"))
                        .collect(Collectors.toList())
        );

        CollectResult result = this.repositorySystem.collectDependencies(this.repositorySystemSession, request);
        DependencyListFlattener visitor = new DependencyListFlattener();
        result.getRoot().accept(visitor);
        List<DependencyNode> nodes = visitor.getNodes();
        this.deps.addAll(
                nodes.stream()
                        .map(n -> new Dep(n.getDependency().getArtifact().getGroupId(), n.getDependency().getArtifact().getArtifactId()))
                        .collect(Collectors.toList())
        );
    }

    protected void writeWildflySwarmClasspathConf() throws IOException {
        Path metaInfDir = Paths.get(this.project.getBuild().getOutputDirectory(), "META-INF");
        Path output = Paths.get(metaInfDir.toString(), "wildfly-swarm-classpath.conf");
        boolean found = Files.exists(output);
        if (!found) {
            Files.createDirectories(metaInfDir);
        }
        try (FileWriter writer = new FileWriter(output.toFile(), found)) {
            if (found) {
                writer.write('\n');
            }

            for (Dep dep : this.deps) {
                writer.write("maven(" + dep.groupId + ":" + dep.artifactId + ") remove\n");
            }

            writer.flush();
        }
    }

    private final Log log;

    private DefaultRepositorySystemSession repositorySystemSession;

    private MavenProject project;

    private RepositorySystem repositorySystem;

    private final List<Dep> deps = new ArrayList<>();

    private static class Dep {

        public final String groupId;

        public final String artifactId;

        Dep(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

    }

    private final class DependencyListFlattener implements DependencyVisitor {

        public DependencyListFlattener() {
            nodes = new ArrayList<>(64);
        }

        public List<DependencyNode> getNodes() {
            return nodes;
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            if (node.getDependency() != null) {
                nodes.add(node);
            }

            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            return true;
        }

        List<DependencyNode> nodes;
    }
}
