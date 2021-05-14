package org.goots.groovy;

import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
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
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GradleScriptTest
                extends AbstractWiremockTest
{

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    static
    {
        // TODO: Remove once GME 2.3 is out.
        //        try
        //        {
        //            LogPrintStream logPrintStream =
        //                            (LogPrintStream) FieldUtils.readDeclaredField( systemErrRule, "logPrintStream", true);
        //            Object m = FieldUtils.readDeclaredField( logPrintStream, "muteableLogStream", true );
        //            FieldUtils.writeDeclaredField( m, "originalStreamMuted", false, true );
        //            FieldUtils.writeDeclaredField( m, "failureLogMuted", true, true );
        //        }
        //        catch ( IllegalAccessException e )
        //        {
        //            e.printStackTrace();
        //        }
    }

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File initScript;

    @Before
    public void setup() throws IOException, URISyntaxException
    {
        String gmeVersion = System.getProperty( "GME_VERSION" );
        initScript = tempDir.newFile();

        System.out.println( "GME_VERSION " + gmeVersion );

        if ( gmeVersion.contains( "SNAPSHOT" ) )
        {
            // Local development
            Path localInitScript = Paths.get( System.getProperty( "user.home" ), ".m2", "repository", "org", "jboss", "gm", "analyzer", gmeVersion, "analyzer-" + gmeVersion + "-init.gradle" );
            Files.copy( localInitScript, initScript.toPath(), StandardCopyOption.REPLACE_EXISTING );
        }
        else
        {
            URL localInitScriptUrl = new URL( "https://repo1.maven.org/maven2/org/jboss/gm/analyzer/" + gmeVersion + "/analyzer-" + gmeVersion + "-init.gradle" );
            FileUtils.copyURLToFile( localInitScriptUrl, initScript );
        }

        stubFor( post( urlEqualTo( "/da/rest/v-1/reports/lookup/gavs" ) ).willReturn( aResponse().withStatus( 200 )
                                                                                                 .withHeader( "Content-Type",
                                                                                                              "application/json;charset=utf-8" )
                                                                                                 .withBody( readSampleDAResponse(
                                                                                                                 "simple-project-with-custom-groovy-script-da-response.json" ) ) ) );
    }

    private void runAlignment( ArrayList<String> args ) throws Exception
    {
        args.add( "-D=gmeFunctionalTest=true" );
        args.add( "--info" );
        // TODO: To enable Gradle debugging
        args.add( "-Dorg.gradle.debug=true" );
        args.add( "generateAlignmentMetadata" );
        new Main().run( args.toArray( new String[] {} ) );
    }

    @Test
    public void verifyBasicGroovyInjection() throws Exception
    {
        final File projectRoot = tempDir.newFolder( "simple-project-with-custom-groovy-script" );
        FileUtils.copyDirectory( Paths.get( GradleScriptTest.class.getClassLoader().getResource( projectRoot.getName() ).toURI() ).toFile(),
                                 projectRoot );
        final URL groovy = Resources.getResource( "gmeBasicDemo.groovy" );

        ArrayList<String> args = new ArrayList<>();
        args.add( "--init-script=" + initScript );
        args.add( "-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1" );
        args.add( "-DgroovyScripts=" + groovy );
        args.add( "--target=" + projectRoot.getAbsolutePath() );

        System.out.println( "Starting with arguments " + args );

        runAlignment( args );

        GMEManipulationModel alignmentModel = new GMEManipulationModel( ManipulationIO.readManipulationModel( projectRoot ) );

        assertTrue( new File( projectRoot, AlignmentTask.GME ).exists() );
        assertTrue( new File( projectRoot, AlignmentTask.GME_PLUGINCONFIGS ).exists() );
        assertEquals( AlignmentTask.INJECT_GME_START, TestUtils.getLine( projectRoot ) );
        assertEquals( AlignmentTask.INJECT_GME_END,
                      org.jboss.gm.common.utils.FileUtils.getLastLine( new File( projectRoot, Project.DEFAULT_BUILD_FILE ) ) );

        assertThat( alignmentModel ).isNotNull().satisfies( am -> {
            assertThat( am.getGroup() ).isEqualTo( "org.acme.gradle" );
            assertThat( am.getName() ).isEqualTo( "newRoot" );
            assertThat( am.findCorrespondingChild( "newRoot" ) ).satisfies( root -> {
                assertThat( root.getVersion() ).isEqualTo( "1.0.1.redhat-00002" );
                assertThat( root.getName() ).isEqualTo( "newRoot" );
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat( alignedDependencies ).extracting( "artifactId", "versionString" )
                                                 .containsOnly( tuple( "undertow-core", "2.0.15.Final-redhat-00001" ),
                                                                tuple( "hibernate-core", "5.3.7.Final-redhat-00001" ) );
            } );
        } );

        // verify that the custom groovy script altered the build script
        final List<String> lines = FileUtils.readLines( new File( projectRoot, "build.gradle" ), Charset.defaultCharset() );
        assertThat( lines ).filteredOn( l -> l.contains( "new CustomVersion" ) )
                           .hasOnlyOneElementSatisfying(
                                           e -> assertThat( e ).contains( "CustomVersion( '1.0.1.redhat-00002', project )" ) );
        assertThat( lines ).filteredOn( l -> l.contains( "undertowVersion =" ) )
                           .hasOnlyOneElementSatisfying( l -> assertThat( l ).contains( "2.0.15.Final-redhat-00001" ) );
        assertTrue( lines.stream().anyMatch( s -> s.contains( "CustomVersion( '1.0.1.redhat-00002', project )" ) ) );
        assertTrue( systemOutRule.getLog().contains( "Attempting to read URL" ) );

        assertThat( FileUtils.readFileToString( new File( projectRoot, "settings.gradle" ), Charset.defaultCharset() ) ).satisfies( s -> {
            assertFalse( s.contains( "x-pack" ) );
            assertTrue( s.contains( "another-pack" ) );
        } );
    }

    @Test
    public void verifyGroovyFirstInjectionIgnored() throws Exception
    {
        final File projectRoot = tempDir.newFolder( "simple-project-with-custom-groovy-script" );
        FileUtils.copyDirectory( Paths.get( GradleScriptTest.class.getClassLoader().getResource( projectRoot.getName() ).toURI() ).toFile(),
                                 projectRoot );
        final URL groovy = Resources.getResource( "gmeGroovyFirst.groovy" );

        ArrayList<String> args = new ArrayList<>();
        args.add( "--init-script=" + initScript );
        args.add( "-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1" );
        args.add( "-DgroovyScripts=" + groovy );
        args.add( "--target=" + projectRoot.getAbsolutePath() );

        System.out.println( "Starting with arguments " + args );

        runAlignment( args );

        assertTrue( systemOutRule.getLog().contains( "For target stage LAST attempting to invoke groovy script" ) );
        assertTrue( systemOutRule.getLog().contains( "InvocationPoint is FIRST" ) );
        assertTrue( systemOutRule.getLog().contains( "Ignoring script org.goots.groovy" ) );
    }

    @Test
    public void verifyGroovyFirstAndLastInjection() throws Exception
    {
        final File projectRoot = tempDir.newFolder( "simple-project-with-custom-groovy-script-wrong-undertow" );
        FileUtils.copyDirectory( Paths.get( GradleScriptTest.class.getClassLoader().getResource( projectRoot.getName() ).toURI() ).toFile(),
                                 projectRoot );

        final URL groovyFirst = Resources.getResource( "gmeGroovyFirst.groovy" );
        final URL groovyLast = Resources.getResource( "gmeBasicDemo.groovy" );

        ArrayList<String> args = new ArrayList<>();
        args.add( "--init-script=" + initScript );
        args.add( "-D" + Configuration.DA + "=" + wireMockRule.baseUrl() + "/da/rest/v-1" );
        args.add( "-DgroovyScripts=" + String.join( ",", groovyFirst.toString(), groovyLast.toString() ) );
        args.add( "--target=" + projectRoot.getAbsolutePath() );

        System.out.println( "Starting with arguments " + args );

        runAlignment( args );

        assertTrue( systemOutRule.getLog().contains( "PASS : Caught Model is not supported for Groovy in initial stage." ) );
        assertTrue( systemOutRule.getLog().contains( "Running Groovy script on" ) );

        GMEManipulationModel alignmentModel = new GMEManipulationModel( ManipulationIO.readManipulationModel( projectRoot ) );

        assertTrue( new File( projectRoot, AlignmentTask.GME ).exists() );
        assertTrue( new File( projectRoot, AlignmentTask.GME_PLUGINCONFIGS ).exists() );
        assertEquals( AlignmentTask.INJECT_GME_START, TestUtils.getLine( projectRoot ) );
        assertEquals( AlignmentTask.INJECT_GME_END,
                      org.jboss.gm.common.utils.FileUtils.getLastLine( new File( projectRoot, Project.DEFAULT_BUILD_FILE ) ) );

        assertThat( alignmentModel ).isNotNull().satisfies( am -> {
            assertThat( am.getGroup() ).isEqualTo( "org.acme.gradle" );
            assertThat( am.getName() ).isEqualTo( "newRoot" );
            assertThat( am.findCorrespondingChild( "newRoot" ) ).satisfies( root -> {
                assertThat( root.getName() ).isEqualTo( "newRoot" );
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat( alignedDependencies ).extracting( "artifactId", "versionString" )
                                                 .containsOnly( tuple( "undertow-core", "2.0.15.Final-redhat-00001" ),
                                                                tuple( "hibernate-core", "5.3.7.Final-redhat-00001" ) );
            } );
        } );

        // verify that the custom groovy script altered the build script
        final List<String> lines = FileUtils.readLines( new File( projectRoot, "build.gradle" ), Charset.defaultCharset() );
        assertThat( lines ).filteredOn( l -> l.contains( "undertowVersion =" ) )
                           .hasOnlyOneElementSatisfying( l -> assertThat( l ).contains( "2.0.15.Final-redhat-00001" ) );
        assertTrue( systemOutRule.getLog().contains( "Attempting to read URL" ) );

        assertThat( FileUtils.readFileToString( new File( projectRoot, "settings.gradle" ), Charset.defaultCharset() ) ).satisfies( s -> {
            assertFalse( s.contains( "x-pack" ) );
            assertTrue( s.contains( "another-pack" ) );
        } );
    }
}
