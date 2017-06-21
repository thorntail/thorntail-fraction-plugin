package org.wildfly.swarm.plugin.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.wildfly.swarm.plugin.repository.PomUtils.extract;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * @author Ken Finnigan
 */
class BomProjectBuilder {

    private static final String DEPENDENCIES_PLACEHOLDER = "FRACTIONS_FROM_BOM";

    private static final String PROPERTIES = "PROPERTIES";

    private static final String SWARM_VERSION = "SWARM_VERSION";

    private static final String BOM_ARTIFACT = "BOM_ARTIFACT";

    private BomProjectBuilder() {
    }

    static File generateProject(final File generatedProjectDir, final File bomFile, final File projectTemplate) throws Exception {
        return preparePom(bomFile, generatedProjectDir, projectTemplate);
    }

    private static File preparePom(File bomFile, File generatedProject, File projectTemplate) throws Exception {
        String dependencies = extract(bomFile, "//dependencyManagement/dependencies/*")
                .asString();
        String properties = extract(bomFile, "//properties/*").asString();
        String swarmVersion = extract(bomFile, "/project/version/text()").asString();
        String bomArtifact = extract(bomFile, "/project/artifactId/text()").asString();
        String pomContent = readTemplate(projectTemplate)
                .replace(DEPENDENCIES_PLACEHOLDER, dependencies)
                .replace(PROPERTIES, properties)
                .replace(SWARM_VERSION, swarmVersion)
                .replace(BOM_ARTIFACT, bomArtifact);
        File pom = new File(generatedProject, "pom.xml");
        pom.createNewFile();
        try (FileWriter writer = new FileWriter(pom)) {
            writer.append(pomContent);
        }
        return pom;
    }

    private static String readTemplate(File projectTemplate) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStreamReader rawReader = new InputStreamReader(new FileInputStream(projectTemplate));
             BufferedReader reader = new BufferedReader(rawReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }
}