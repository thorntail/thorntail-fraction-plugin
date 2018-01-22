package org.wildfly.swarm.plugin.repository;

import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.wildfly.swarm.plugin.repository.PomUtils.extract;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * @author Ken Finnigan
 */
class BomProjectBuilder {

    private static final String DEPENDENCIES_PLACEHOLDER = "FRACTIONS_FROM_BOM";

    private static final String PROPERTIES = "PROPERTIES";

    private static final String SWARM_VERSION = "SWARM_VERSION";

    private static final String BOM_GROUPID = "BOM_GROUPID";
    private static final String BOM_ARTIFACT = "BOM_ARTIFACT";
    public static final String NEWLINE = "\n";

    private BomProjectBuilder() {
    }

    static File generateProject(final File generatedProject,
                                final File bomFile,
                                final File projectTemplate,
                                final MavenProject bomProject,
                                final String[] skipBomDependencies,
                                final File additionalBom) throws Exception {


        String properties = extract(bomFile, "//properties/*").asString();

        List<XmlDependencyElement> dependencies = getDependencies(bomFile, skipBomDependencies);

        List<XmlDependencyElement> additionalDependencies = Collections.emptyList();
        if (additionalBom != null) {
            additionalDependencies = getDependencies(additionalBom, skipBomDependencies)
                    .stream()
                    .filter(XmlDependencyElement::isNotImportScoped)
                    .collect(Collectors.toList());
        }

        additionalDependencies.removeAll(dependencies);

        String bomDependenciesAsString = dependencies.stream()
                .map(XmlDependencyElement::getElementAsString)
                .collect(Collectors.joining(NEWLINE));

        String additionalBomDependenciesAsString = additionalDependencies.stream()
                .map(element -> element.toDependencyWithScope("test"))
                .collect(Collectors.joining(NEWLINE));

        String dependenciesAsString = bomDependenciesAsString + NEWLINE + additionalBomDependenciesAsString;

        String pomContent = readTemplate(projectTemplate)
                .replace(DEPENDENCIES_PLACEHOLDER, dependenciesAsString)
                .replace(PROPERTIES, properties)
                .replace(SWARM_VERSION, bomProject.getVersion())
                .replace(BOM_GROUPID, bomProject.getGroupId())
                .replace(BOM_ARTIFACT, bomProject.getArtifactId());
        File pom = new File(generatedProject, "pom.xml");
        pom.createNewFile();
        try (FileWriter writer = new FileWriter(pom)) {
            writer.append(pomContent);
        }
        return pom;
    }

    private static List<XmlDependencyElement> getDependencies(File additionalBom, String... expressionsToSkip) {
        PomUtils.XmlToString result =
                PomUtils.extract(additionalBom, "//dependencyManagement/dependencies/*")
                        .skipping(expressionsToSkip);

        return result.translate(XmlDependencyElement::fromNode);
    }

    private static String readTemplate(File projectTemplate) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStreamReader rawReader = new InputStreamReader(new FileInputStream(projectTemplate));
             BufferedReader reader = new BufferedReader(rawReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append(NEWLINE);
            }
        }
        return result.toString();
    }

}