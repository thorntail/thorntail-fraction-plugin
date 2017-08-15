/*
 * Copyright 2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author Ken Finnigan
 * @author Martin Kouba
 */
@Mojo(name = "generate-licenses", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class LicenseMojo extends RepositoryBuilderMojo {

    private static final String LICENCE_PROJECT_DIR = "license-project";

    private static final String LICENCE_POM_TEMPLATE = "license-pom-template.xml";

    private static final String DEPENDENCIES_PLACEHOLDER = "#{dependencies}";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // When executing in license mode only, we don't want to generate the repository zip or attach it to artifacts to be installed
        this.generateZip = false;

        // Execute parent Mojo that will generate project from bom
        super.execute();

        try {
            // Search through repository for pom.xmls, retrieving dependency information
            Set<Path> pomPaths = new HashSet<>();
            Set<Path> jarPaths = new HashSet<>();

            searchRepository(repoDir.toPath(), pomPaths::add, jarPaths::add);

            // Process poms and jars to retrieve artifact details
            List<Dependency> dependencies = new ArrayList<>();

            pomPaths.forEach(p -> convertPomToDependency(p, dependencies::add));
            jarPaths.forEach(j -> convertJarToDependency(j, dependencies::add));

            // Use dependency list to create a new pom.xml in temporary project
            File licensePomFile = getLicensePomFile();
            createLicensePom(dependencies, licensePomFile);

            // Run Maven build to update license information
            executeLicenseProject(licensePomFile, repoDir);

            // Copy licenses.xml result to output directory
            if (outputDirectory != null) {
                outputDirectory.mkdirs();
                if (outputDirectory.isDirectory()) {
                    File outputFile = new File(outputDirectory, "licenses.xml");
                    Files.copy(new File(this.project.getBuild().getDirectory() + "/" + LICENCE_PROJECT_DIR + "/target/licenses.xml").toPath(),
                            outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLog().info("licenses.xml copied to: " + outputFile.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private File getLicensePomFile() {
        File tmpProjectDir = new File(this.project.getBuild().getDirectory(), LICENCE_PROJECT_DIR);
        tmpProjectDir.mkdirs();
        return new File(tmpProjectDir, "pom.xml");
    }

    private void createLicensePom(List<Dependency> dependencies, File licensePomFile) throws IOException {
        String template = loadTemplate();
        // First clear the license project directory
        Files.walkFileTree(licensePomFile.getParentFile().toPath(), new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(licensePomFile.getParentFile().toPath())) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // Create a new pom.xml from template
        Files.write(licensePomFile.toPath(), template
                .replace(DEPENDENCIES_PLACEHOLDER, dependencies.stream().map(d -> d.asPomElement()).collect(Collectors.joining("\n"))).getBytes("UTF-8"));
    }

    private String loadTemplate() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(LicenseMojo.class.getClassLoader().getResourceAsStream(LICENCE_POM_TEMPLATE), "UTF-8"))) {
            StringWriter writer = new StringWriter();
            final char[] buffer = new char[1024 * 8];
            int n = 0;
            while (-1 != (n = reader.read(buffer))) {
                writer.write(buffer, 0, n);
            }
            writer.flush();
            return writer.toString();
        }
    }

    private void executeLicenseProject(File pomFile, File repoDir) throws Exception {
        InvocationRequest mavenRequest = new DefaultInvocationRequest();
        mavenRequest.setPomFile(pomFile);
        mavenRequest.setBaseDirectory(pomFile.getParentFile());
        mavenRequest.setUserSettingsFile(userSettings);
        mavenRequest.setLocalRepositoryDirectory(repoDir);
        mavenRequest.setGoals(Arrays.asList(new String[] {"clean", "package"}));

        Invoker invoker = new DefaultInvoker();
        try {
            InvocationResult result = invoker.execute(mavenRequest);
            if (result.getExitCode() != 0) {
                throw result.getExecutionException() != null ? result.getExecutionException()
                        : new IllegalStateException("Build failure: " + result.getExitCode());
            }
            getLog().info("Licenses POM executed: " + pomFile.getAbsolutePath());
        } catch (Exception e) {
            getLog().error("Error when executing " + pomFile.getAbsolutePath(), e);
        }
    }

    private void searchRepository(Path repositoryDirectory, Consumer<Path> pomConsumer, Consumer<Path> jarConsumer) throws IOException {
        Files.walkFileTree(repositoryDirectory, new SimpleFileVisitor<Path>() {

            boolean hasPom = false;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String[] pomList = dir.toFile().list((dir1, name) -> name.endsWith(".pom"));

                if (pomList != null && pomList.length >= 1) {
                    hasPom = true;
                }

                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!isExcluded(file)) {
                    if (file.toString().endsWith(".pom")) {
                        pomConsumer.accept(file);
                    } else if (!hasPom && file.toString().endsWith(".jar")) {
                        jarConsumer.accept(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                hasPom = false;
                return super.postVisitDirectory(dir, exc);
            }
        });
    }

    private boolean isExcluded(Path file) {
        // The air gap repo does not contain any community bits but few more are downloaded as various maven plugin dependencies
        // when executing license project template
        if (isRemoveCommunity() && !isProductizedArtifact(file)) {
            return true;
        }
        if (excludes == null || excludes.length < 1) {
            return false;
        }
        for (String exclude : excludes) {
            if (repoDir.toPath().relativize(file).toString().matches(exclude)) {
                getLog().info("Excluding " + file);
                return true;
            }
        }
        return false;
    }

    private void convertPomToDependency(Path pomPath, Consumer<Dependency> dependencyConsumer) {
        try {

            Dependency dep = new Dependency();
            Document pom = parsePom(pomPath.toFile());

            dep.groupId = groupIdExpression().evaluate(pom);
            dep.artifactId = artifactIdExpression().evaluate(pom);
            dep.version = versionExpression().evaluate(pom);
            dep.packaging = packagingExpression().evaluate(pom);

            if (dep.groupId == null || dep.groupId.isEmpty()) {
                dep.groupId = parentGroupIdExpression().evaluate(pom);
            }
            if (dep.version == null || dep.version.isEmpty()) {
                dep.version = parentVersionExpression().evaluate(pom);
            }
            if (dep.packaging == null || dep.packaging.isEmpty() || isJarPackaging(dep.packaging)) {
                dep.packaging = "jar";
            }
            dependencyConsumer.accept(dep);

        } catch (IOException | SAXException | ParserConfigurationException | XPathExpressionException e) {
            getLog().error(e);
        }
    }

    private boolean isJarPackaging(String packagingType) {
        return "bundle".equals(packagingType) || packagingType.startsWith("eclipse-");
    }

    private void convertJarToDependency(Path jarPath, Consumer<Dependency> dependencyConsumer) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<JarEntry> entries = jarFile.stream().filter(entry -> entry.getName().contains("pom.properties")).collect(Collectors.toList());

            Dependency dep = new Dependency();

            if (entries.size() == 1) {

                try (Scanner scanner = new Scanner(jarFile.getInputStream(entries.get(0)))) {
                    while (scanner.hasNext()) {
                        String line = scanner.nextLine();

                        if (line.startsWith("groupId")) {
                            dep.groupId = line.substring(line.indexOf('=') + 1).trim();
                        } else if (line.startsWith("artifactId")) {
                            dep.artifactId = line.substring(line.indexOf('=') + 1).trim();
                        } else if (line.startsWith("version")) {
                            dep.version = line.substring(line.indexOf('=') + 1).trim();
                        }
                    }
                }
            } else {
                // Didn't find pom.properties so try MANIFEST.MF
                entries = jarFile.stream().filter(entry -> entry.getName().contains("MANIFEST.MF")).collect(Collectors.toList());

                try (Scanner scanner = new Scanner(jarFile.getInputStream(entries.get(0)))) {
                    while (scanner.hasNext()) {
                        String line = scanner.nextLine();

                        if (line.startsWith("Implementation-Vendor-Id:")) {
                            dep.groupId = line.substring(line.indexOf(':') + 1).trim();
                        } else if (line.startsWith("Implementation-Title:")) {
                            dep.artifactId = line.substring(line.indexOf(':') + 1).trim();
                        } else if (line.startsWith("Implementation-Version:")) {
                            dep.version = line.substring(line.indexOf(':') + 1).trim();
                        }
                    }
                }
            }

            String artifactName = jarPath.toAbsolutePath().toString();

            if (!dep.isComplete()) {
                getLog().warn("Skipping incomplete dependency: " + dep.toString() + " for " + artifactName);
                return;
            }

            if (!artifactName.substring(artifactName.lastIndexOf('.') + 1).endsWith(dep.version)) {
                // Auto created pom.properties/manifest does not contain classifier
                // we can do nothing but ignore such arfitact
                getLog().warn("Skipping artifact with classifier: " + artifactName);
                return;
            }

            dependencyConsumer.accept(dep);

        } catch (IOException e) {
            getLog().error(e);
        }
    }

    private Document parsePom(File pom) throws IOException, SAXException, ParserConfigurationException {
        final DocumentBuilder documentBuilder = documentBuilder();
        return documentBuilder.parse(pom);
    }

    private DocumentBuilder documentBuilder() throws ParserConfigurationException {
        if (documentBuilder == null) {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        }
        return documentBuilder;
    }

    private XPath xPath() {
        if (xpath == null) {
            XPathFactory xPathFactory = XPathFactory.newInstance();
            xpath = xPathFactory.newXPath();
        }
        return xpath;
    }

    private XPathExpression parentGroupIdExpression() throws XPathExpressionException {
        if (parentGroupIdExpression == null) {
            parentGroupIdExpression = xPath().compile("/project/parent/groupId");
        }
        return parentGroupIdExpression;
    }

    private XPathExpression parentVersionExpression() throws XPathExpressionException {
        if (parentVersionExpression == null) {
            parentVersionExpression = xPath().compile("/project/parent/version");
        }
        return parentVersionExpression;
    }

    private XPathExpression groupIdExpression() throws XPathExpressionException {
        if (groupIdExpression == null) {
            groupIdExpression = xPath().compile("/project/groupId");
        }
        return groupIdExpression;
    }

    private XPathExpression artifactIdExpression() throws XPathExpressionException {
        if (artifactIdExpression == null) {
            artifactIdExpression = xPath().compile("/project/artifactId");
        }
        return artifactIdExpression;
    }

    private XPathExpression versionExpression() throws XPathExpressionException {
        if (versionExpression == null) {
            versionExpression = xPath().compile("/project/version");
        }
        return versionExpression;
    }

    private XPathExpression packagingExpression() throws XPathExpressionException {
        if (packagingExpression == null) {
            packagingExpression = xPath().compile("/project/packaging");
        }
        return packagingExpression;
    }

    @Parameter
    protected File outputDirectory;

    /**
     * List of regular expressions used to exclude files from the repository. Only the part relative to the repo is matched for a file path, e.g.
     * <code>org/jboss/weld/se/weld-se-core/2.3.5.Final/weld-se-core-2.3.5.Final.jar</code>.
     */
    @Parameter
    private String[] excludes;

    private XPath xpath;
    private XPathExpression parentGroupIdExpression;
    private XPathExpression parentVersionExpression;
    private XPathExpression groupIdExpression;
    private XPathExpression artifactIdExpression;
    private XPathExpression versionExpression;
    private XPathExpression packagingExpression;
    private DocumentBuilder documentBuilder;

    class Dependency {

        // Also exclude all transitive dependencies
        private static final String POM_FORMAT = "<dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions><type>%s</type></dependency>";

        String groupId;

        String artifactId;

        String version;

        String packaging = "jar";

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Dependency that = (Dependency) o;
            return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, version);
        }

        @Override
        public String toString() {
            return groupId + ':' + artifactId + ':' + packaging + ':' + version;
        }

        String asPomElement() {
            return String.format(POM_FORMAT, groupId, artifactId, version, packaging);
        }

        boolean isComplete() {
            return groupId != null && artifactId != null && version != null && packaging != null;
        }

    }

}
