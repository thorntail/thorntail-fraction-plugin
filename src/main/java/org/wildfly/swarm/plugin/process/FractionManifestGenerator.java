package org.wildfly.swarm.plugin.process;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.plugin.DependencyMetadata;
import org.wildfly.swarm.plugin.FractionMetadata;
import org.wildfly.swarm.plugin.StabilityLevel;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Bob McWhirter
 */
public class FractionManifestGenerator implements Function<FractionMetadata, FractionMetadata> {

    private final Log log;

    private final MavenProject project;

    public FractionManifestGenerator(Log log, MavenProject project) {
        this.log = log;
        this.project = project;
    }

    public FractionMetadata apply(FractionMetadata meta) {
        Properties props = new Properties();

        StabilityLevel stabilityLevel = StabilityLevel.of(this.project);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml( options );

        Map<String,Object> data = new LinkedHashMap<String,Object>() {{
            //noinspection unchecked
            put("name", meta.getName());
            put("description", meta.getDescription());
            put("groupId", meta.getGroupId());
            put("artifactId", meta.getArtifactId());
            put("version", meta.getVersion());
            if ( meta.hasJavaCode() && meta.getModule() != null) {
                put("module", meta.getModule());
            }
            put("stability", new HashMap<String,Object>() {{
                put("level", meta.getStabilityIndex().toString());
                put("index", meta.getStabilityIndex().ordinal());
            }});
            put("internal", meta.isInternal());
            put("dependencies",
                    meta.getDependencies()
                            .stream()
                            .map(DependencyMetadata::toString)
                            .collect(Collectors.toList())
            );
        }};

        Path file = Paths.get(this.project.getBuild().getOutputDirectory(), "META-INF", "fraction-manifest.yaml");
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = new FileWriter(file.toFile())) {
                yaml.dump(data, out);
            }
        } catch (IOException e) {
            this.log.error(e.getMessage(), e);
        }

        return meta;
    }
}
