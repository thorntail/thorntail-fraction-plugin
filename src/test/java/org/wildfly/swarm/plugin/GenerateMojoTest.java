package org.wildfly.swarm.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@RunWith(Parameterized.class)
public class GenerateMojoTest {

    @Rule
    public MojoRule rule = new MojoRule();

    @Rule
    public TestResources resources = new TestResources();

    @Parameterized.Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[][]{
                // SWARM-365: Uncomment to reproduce the bug
                // {"addons", "keycloak-adapter-feature-pack-1.9.1.Final.zip"},
                {"modules", "keycloak-adapter-feature-pack-1.8.1.Final.zip"}
        });
    }

    @Parameterized.Parameter // first data value (0) is default
    public String testProject;

    @Parameterized.Parameter(value = 1)
    public String featurePackZip;


    @Test
    public void testAnalyzer() throws Exception {
        // Fetch project's pom.xml
        File projectCopy = this.resources.getBasedir(testProject);
        File pom = new File(projectCopy, "pom.xml");
        Assert.assertNotNull(pom);
        Assert.assertTrue(pom.exists());


        GenerateMojo mojo = (GenerateMojo) rule.lookupMojo("generate", pom);
        Assert.assertNotNull(mojo);

        // Create the Maven project manually
        Model model = new DefaultModelReader().read(pom, Collections.emptyMap());
        final MavenProject mvnProject = new MavenProject(model);
        mvnProject.setFile(pom);
        Set<Artifact> artifactList = new HashSet<>();

        {
            DefaultArtifact da = new DefaultArtifact("org.keycloak", "keycloak-adapter-feature-pack", "1", "compile", "zip", null, new DefaultArtifactHandlerStub("zip"));
            da.setFile(new File(projectCopy, featurePackZip));
            artifactList.add(da);
        }
        {
            DefaultArtifact da = new DefaultArtifact("org.wildfly", "wildfly-core-feature-pack", "2.0.10.Final", "compile", "zip", null, new DefaultArtifactHandlerStub("zip"));
            da.setFile(new File(projectCopy, "wildfly-core-feature-pack-2.0.10.Final.zip"));
            artifactList.add(da);
        }
        {
            DefaultArtifact da = new DefaultArtifact("org.wildfly", "wildfly-feature-pack", "10.0.0.Final", "compile", "zip", null, new DefaultArtifactHandlerStub("zip"));
            da.setFile(new File(projectCopy, "wildfly-feature-pack-10.0.0.Final.zip"));
            artifactList.add(da);
        }
        {
            DefaultArtifact da = new DefaultArtifact("org.wildfly", "wildfly-servlet-feature-pack", "10.0.0.Final", "compile", "zip", null, new DefaultArtifactHandlerStub("zip"));
            da.setFile(new File(projectCopy, "wildfly-servlet-feature-pack-10.0.0.Final.zip"));
            artifactList.add(da);
        }
        mvnProject.setArtifacts(artifactList);

        rule.setVariableValueToObject(mojo, "project", mvnProject);
        rule.setVariableValueToObject(mojo, "repositorySystemSession", new DefaultRepositorySystemSession());

        mojo.execute();
    }
}
