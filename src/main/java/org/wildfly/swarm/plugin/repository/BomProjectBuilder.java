package org.wildfly.swarm.plugin.repository;

import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.wildfly.swarm.plugin.repository.PomUtils.extract;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * @author Ken Finnigan
 */
class BomProjectBuilder {

    private static final String BOMS = "BOMS";

    private static final String DEPENDENCIES_PLACEHOLDER = "FRACTIONS_FROM_BOM";

    private static final String PROPERTIES = "PROPERTIES";

    private static final String SWARM_VERSION = "SWARM_VERSION";

    private static final String BOM_GROUPID = "BOM_GROUPID";
    private static final String BOM_ARTIFACT = "BOM_ARTIFACT";
    public static final String NEWLINE = "\n";

    private BomProjectBuilder() {
    }

    static File generateProject(final File generatedProject,
                                final File projectTemplate,
                                final MavenProject bomProject,
                                final File[] bomFiles) throws Exception {

        String properties = extract(bomFiles[0], "//properties/*").asString();

        String bomsAsString = Stream.of(bomFiles)
                .map(file -> BomProjectBuilder.createPomImportXml(file, bomProject.getVersion()))
                .collect(Collectors.joining(NEWLINE));

        String dependenciesAsString = Stream.of(bomFiles)
                .flatMap(file -> getDependencies(file).stream())
                .filter(XmlDependencyElement::isNormalDependency)
                .map(XmlDependencyElement::getElementAsString)
                .distinct()
                .collect(Collectors.joining(NEWLINE));

        String pomContent = readTemplate(projectTemplate)
                .replace(BOMS, bomsAsString)
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


    private static String createPomImportXml(File file, String version) {
        String groupId = extract(file, "/project/groupId").asString();
        String projectId = extract(file, "/project/artifactId").asString();
        return String.format(
                "<dependency>\n" +
                        "        %s\n" +
                        "        %s\n" +
                        "        <version>%s</version>\n" +
                        "        <scope>import</scope>\n" +
                        "        <type>pom</type>\n" +
                        "      </dependency>",
                groupId, projectId, version
        );
    }

    private static List<XmlDependencyElement> getDependencies(File additionalBom) {
        PomUtils.XmlToString result =
                PomUtils.extract(additionalBom, "//dependencyManagement/dependencies/*");

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