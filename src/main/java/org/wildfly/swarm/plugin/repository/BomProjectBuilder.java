package org.wildfly.swarm.plugin.repository;

import static org.wildfly.swarm.plugin.repository.PomUtils.extract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.maven.project.MavenProject;

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

    private BomProjectBuilder() {
    }

    static File generateProject(final File generatedProjectDir,
                                final File bomFile,
                                final File projectTemplate,
                                final MavenProject bomProject,
                                final String[] skipBomDependencies) throws Exception {
        return preparePom(bomFile, generatedProjectDir, projectTemplate, bomProject, skipBomDependencies);
    }

    private static File preparePom(File bomFile,
                                   File generatedProject,
                                   File projectTemplate,
                                   MavenProject bomProject,
                                   String[] skipBomDependencies) throws Exception {
        String dependencies = extract(bomFile, "//dependencyManagement/dependencies/*")
                .skipping(skipBomDependencies)
                .asString();
        String properties = extract(bomFile, "//properties/*").asString();
        String pomContent = readTemplate(projectTemplate)
                .replace(DEPENDENCIES_PLACEHOLDER, dependencies)
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