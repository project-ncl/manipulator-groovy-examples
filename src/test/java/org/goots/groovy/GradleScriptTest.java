package org.goots.groovy;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jboss.gm.analyzer.alignment.AbstractWiremockTest;
import org.jboss.gm.analyzer.alignment.AlignmentTask;
import org.jboss.gm.analyzer.alignment.TestUtils;
import org.jboss.gm.cli.Main;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.gradle.tooling.GradleConnector.newConnector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GradleScriptTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private GradleConnector connector;

    private File initScript;

    @Before
    public void setup() throws IOException, URISyntaxException
    {
        String gmeVersion = System.getProperty("GME_VERSION");
        initScript = tempDir.newFile();

        System.out.println ("GME_VERSION " + gmeVersion);

        if ( gmeVersion.contains( "SNAPSHOT" ) )
        {
            // Local development
            Files.copy( Paths.get(
                            System.getProperty( "user.home" ) + File.separator + ".m2/repository/org/jboss/gm/analyzer"
                                            + File.separator + gmeVersion + File.separator + "analyzer-" + gmeVersion
                                            + "-init.gradle" ),
                        initScript.toPath(),
                        StandardCopyOption.REPLACE_EXISTING );
        }
        else
        {
            FileUtils.copyURLToFile( new URL( "http://central.maven.org/maven2/org/jboss/gm/analyzer" + File.separator
                                                              + gmeVersion + File.separator + "analyzer-" + gmeVersion
                                                              + "-init.gradle" ), initScript );
        }

        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                               .willReturn(aResponse().withStatus(200)
                               .withHeader("Content-Type", "application/json;charset=utf-8")
                               .withBody(readSampleDAResponse("simple-project-with-custom-groovy-script-da-response.json" ) ) ) );

       connector = newConnector().useGradleVersion("5.6.2");
    }


    private void runAlignment(File projectRoot, ArrayList<String> args)
    {
        args.add( "-D=gmeFunctionalTest=true" );
        try ( ProjectConnection connection = connector.forProjectDirectory(projectRoot).connect())
        {
            BuildLauncher buildLauncher = connection.newBuild();
            buildLauncher.setStandardError(System.err);
            buildLauncher.setColorOutput(true);
            buildLauncher.setStandardOutput(System.out);
            //noinspection UnstableApiUsage
            buildLauncher.addArguments(args);
            buildLauncher.forTasks("generateAlignmentMetadata");
            buildLauncher.run();
        }
    }

    @Test
    public void verifyBasicGroovyInjection() throws IOException, URISyntaxException, ManipulationException
    {
        final File projectRoot = tempDir.newFolder("simple-project-with-custom-groovy-script");
        FileUtils.copyDirectory(Paths
                .get(GradleScriptTest.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        final File groovy = GroovyLoader.loadGroovy("gmeBasicDemo.groovy");

        ArrayList<String> args = new ArrayList<>();
        args.add("--info");
        args.add("--init-script=" + initScript);
        args.add("-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1");
        args.add("-DgroovyScripts=file://" + groovy);

        System.out.println ("Starting with arguments " + args);

        runAlignment( projectRoot, args );

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

    @Test
    public void verifyGroovyFirstInjectionIgnored() throws IOException, URISyntaxException
    {
        final File projectRoot = tempDir.newFolder("simple-project-with-custom-groovy-script");
        FileUtils.copyDirectory(Paths.get(GradleScriptTest.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        final File groovy = GroovyLoader.loadGroovy("gmeGroovyFirst.groovy");

        ArrayList<String> args = new ArrayList<>();
        args.add("--debug");
        args.add("--init-script=" + initScript);
        args.add("-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1");
        args.add("-DgroovyScripts=file://" + groovy);

        System.out.println ("Starting with arguments " + args);

        runAlignment( projectRoot, args );

        assertTrue( systemOutRule.getLog().contains( "For target stage LAST attempting to invoke groovy script" ) );
        assertTrue( systemOutRule.getLog().contains( "InvocationPoint is FIRST" ) );
        assertTrue( systemOutRule.getLog().contains( "Ignoring script org.goots.groovy") );
    }

    @Test
    public void verifyGroovyFirstAndLastInjection() throws Exception {

        final File projectRoot = tempDir.newFolder("simple-project-with-custom-groovy-script-wrong-undertow");
        FileUtils.copyDirectory(Paths.get(GradleScriptTest.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);

        final File groovyFirst = GroovyLoader.loadGroovy("gmeGroovyFirst.groovy");
        final File groovyLast = GroovyLoader.loadGroovy("gmeBasicDemo.groovy");

        Main m = new Main();
        ArrayList<String> args = new ArrayList<>();
        args.add("--info");
        args.add("--target=" + projectRoot.getAbsolutePath());
        args.add("--init-script=" + initScript);
        args.add("-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1");
        args.add("-DgroovyScripts=file://" + groovyFirst + ",file://" + groovyLast);
        args.add("generateAlignmentMetadata" );


        System.out.println ("Starting with arguments " + args);

        m.run(args.toArray(new String[]{}));

        assertTrue( systemOutRule.getLog().contains( "PASS : Caught Model is not supported for Groovy in initial stage." ) );
        assertTrue( systemOutRule.getLog().contains( "Running Groovy script on"));


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
        assertThat(lines).filteredOn(l -> l.contains("undertowVersion ="))
                         .hasOnlyOneElementSatisfying(l -> assertThat(l).contains("2.0.15.Final-redhat-00001"));
        assertTrue(systemOutRule.getLog().contains("Attempting to read URL"));

        assertThat(FileUtils.readFileToString(new File(projectRoot, "settings.gradle"), Charset.defaultCharset()))
                        .satisfies(s -> {
                            assertFalse(s.contains("x-pack"));
                            assertTrue(s.contains("another-pack"));
                        });
    }

}
