package org.goots.groovy;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.jboss.gm.analyzer.alignment.AbstractWiremockTest;
import org.jboss.gm.analyzer.alignment.AlignmentTask;
import org.jboss.gm.analyzer.alignment.TestUtils;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GradleScriptTest extends AbstractWiremockTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();//.muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private org.gradle.tooling.GradleConnector connector;

    private File initScript;

    @Before
    public void setup() throws IOException {

        System.out.println ("GME_VERSION " + System.getProperty("GME_VERSION"));

        initScript = tempDir.newFile();
        FileUtils.copyURLToFile(new URL ("http://central.maven.org/maven2/org/jboss/gm/analyzer/analyzer/1.2/analyzer-1.2-init.gradle"), initScript);
        FileUtils.writeStringToFile(initScript,
                FileUtils.readFileToString(initScript, Charset.defaultCharset()).
                        replaceAll(":analyzer:.*", ":analyzer:" + System.getProperty("GME_VERSION") + "\""),
                Charset.defaultCharset());



        URL resource = Thread.currentThread().getContextClassLoader().getResource("simple-project-with-custom-groovy-script-da-response.json");

        if ( resource == null )
        {
            throw new RuntimeException();
        }
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(FileUtils.readFileToString ( new File ( resource.getPath()) , Charset.defaultCharset() ))));

        connector = org.gradle.tooling.GradleConnector.newConnector().useGradleVersion("5.6.2");
    }

    @Test
    public void verifyGroovyInjection() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project-with-custom-groovy-script");
        FileUtils.copyDirectory(Paths
                .get(GradleScriptTest.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);

        ArrayList<String> args = new ArrayList<>();
        args.add("--info");
        args.add("--init-script=" + initScript);
        args.add("-D" + Configuration.DA + "=http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
        args.add("-DgroovyScripts=file://" + projectRoot + "/gme.groovy");
        args.add("generateAlignmentMetadata");

        try ( ProjectConnection connection = connector.forProjectDirectory(projectRoot).connect() )
        {
            BuildLauncher buildLauncher = connection.newBuild().withArguments(args);
            buildLauncher.setStandardError(System.err);
            buildLauncher.setColorOutput(true);
            buildLauncher.setStandardOutput(System.out);
            buildLauncher.run();
        }

        TestUtils.TestManipulationModel alignmentModel = new TestUtils.TestManipulationModel(ManipulationIO.readManipulationModel(projectRoot));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END,
                org.jboss.gm.common.utils.FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("newRoot");
            assertThat(am.findCorrespondingChild("newRoot")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("newRoot");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });

        // verify that the custom groovy script altered the build script
        final List<String> lines = FileUtils.readLines(new File(projectRoot, "build.gradle"), Charset.defaultCharset());
        assertThat(lines).filteredOn(
                l -> l.contains("new CustomVersion"))
                .hasOnlyOneElementSatisfying(e -> assertThat(e).contains("CustomVersion( '1.0.1.redhat-00002', project )"));
        assertThat(lines).filteredOn(l -> l.contains("undertowVersion ="))
                .hasOnlyOneElementSatisfying(l -> assertThat(l).contains("2.0.15.Final-redhat-00001"));
        assertTrue(lines.stream().anyMatch(s -> s.contains("CustomVersion( '1.0.1.redhat-00002', project )")));
        assertTrue(systemOutRule.getLog().contains("Attempting to read URL"));

        assertThat(FileUtils.readFileToString(new File(projectRoot, "settings.gradle"), Charset.defaultCharset()))
                .satisfies(s -> {
                    assertFalse(s.contains("x-pack"));
                    assertTrue(s.contains("another-pack"));
                });
    }
}
