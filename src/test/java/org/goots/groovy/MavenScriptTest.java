package org.goots.groovy;

import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.commonjava.maven.atlas.ident.ref.SimpleArtifactRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationManager;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.fixture.TestUtils.SMContainer;
import org.commonjava.maven.ext.core.groovy.BaseScript;
import org.commonjava.maven.ext.core.impl.FinalGroovyManipulator;
import org.commonjava.maven.ext.core.impl.Manipulator;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MavenScriptTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testQuarkusGroovyAnnotation() throws Exception
    {
        final File preGroovy = GroovyLoader.loadGroovy( "quarkusPlatformPre.groovy" );
        final File quarkusFolder = temp.newFolder();
        final File mvnRepo = temp.newFolder();
        final File rootPom = new File( quarkusFolder.getCanonicalFile(), "pom.xml");

        Git.cloneRepository()
           .setURI("https://github.com/quarkusio/quarkus-platform.git")
           .setDirectory(quarkusFolder)
           .setBranch( "refs/tags/1.3.1.Final")
           .call();
        System.out.println ("Cloned Quarkus to " + quarkusFolder);

        // While it would be nice to be able to just do "new Cli().run(....)" due to project setup complexities (e.g. running
        // inside Maven, Gradle dependencies etc) we get Google Inject CreationErrors. Therefore use the following block to
        // replicate the CLI
        Properties prop = new Properties();
        prop.setProperty( "groovyScripts", "file://" + preGroovy.getAbsolutePath() );
        prop.setProperty( "maven.repo.local", mvnRepo.toString() );
        SMContainer smc = TestUtils.createSessionAndManager( prop );
        smc.getRequest().setPom( rootPom );
        smc.getManager().scanAndApply( smc.getSession() );

        List<Project> result = new PomIO().parseProject( rootPom );
        assertTrue( new File(quarkusFolder, "bom/product-bom").exists() );
        assertEquals( 5, result.get( 0 ).getModel().getModules().size() );
        assertEquals( result.stream().filter( p -> p.getArtifactId().equals( "quarkus-product-bom" ) )
                            .map( pr -> pr.getModel().getDependencyManagement().getDependencies().size() ).findFirst().orElse( 0 ),
                      Integer.valueOf( 445 ) );
    }

    @Test
    public void testGroovyAnnotation() throws Exception {
        final File groovy = GroovyLoader.loadGroovy("pmeBasicDemo.groovy");
        final File pRoot = new File(TestUtils.resolveFileResource("", "")
                .getParentFile()
                .getParentFile(), "pom.xml");
        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(pRoot);
        ManipulationManager m = new ManipulationManager(Collections.emptyMap(), Collections.emptyMap(), null);
        ManipulationSession ms = TestUtils.createSession(null);
        m.init(ms);

        // We could use the scanAndApply method in testQuarkusGroovyAnnotation but we don't want to alter the root pom.

        Project root = projects.stream().filter(p -> p.getProjectParent()==null).findAny().orElse(null);
        Manipulator gm = new FinalGroovyManipulator( null, null, null);
        gm.init(ms);
        TestUtils.executeMethod(gm, "applyGroovyScript", new Class[]{List.class, Project.class, File.class},
                new Object[]{projects, root, groovy});
        assertTrue( systemOutRule.getLog().contains( "BASESCRIPT") );

        List<String> result = projects.get(0).getModel().getDependencies().stream().
                filter(d -> d.getGroupId().equals("org.apache.maven") && d.getArtifactId().equals("maven-core")).
                map(Dependency::getVersion).collect(Collectors.toList());

        assertEquals(1, result.size());
        assertEquals("3.5.0", result.get(0));
    }

    @Test
    public void resolverTest() throws IOException, ManipulationException {
        final ManipulationSession session = TestUtils.createSession( null );
        final ModelIO model = new ModelIO
                (new GalleyAPIWrapper(
                        new GalleyInfrastructure( session, null)
                                .init( null, null, temp.newFolder("cache-dir" ) )));

        File c = model.resolveRawFile( SimpleArtifactRef.parse( "academy.alex:custommatcher:1.0"  ) );
        TestCase.assertTrue (c.exists());
        TestCase.assertTrue (FileUtils.readFileToString( c, Charset.defaultCharset()).contains( "This is Custom Matcher to validate Credit Card" ));
    }

    @Test
    public void testInlineProperty() throws Exception
    {
        final ManipulationSession session = TestUtils.createSession(null);
        final ModelIO model = new ModelIO
                (new GalleyAPIWrapper(
                        new GalleyInfrastructure( session, null)
                                .init( null, null, temp.newFolder("cache-dir" ) )));

        final File pme = model.resolveRawFile( SimpleArtifactRef.parse( "org.commonjava.maven.ext:pom-manipulation-parent:3.8" ) );

        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject( pme );
        ManipulationManager m = new ManipulationManager( Collections.emptyMap(), Collections.emptyMap(), null );
        ManipulationSession ms = TestUtils.createSession( null );
        m.init( ms );

        Project root = projects.stream().filter( p -> p.getProjectParent() == null ).findAny().orElseThrow(Exception::new);

        BaseScript bs = new BaseScript()
        {
            @Override
            public Object run()
            {
                return null;
            }
        };
        bs.setValues( null, null, null, ms, projects, root, null );

        bs.inlineProperty( root, SimpleProjectRef.parse( "org.commonjava.maven.atlas:atlas-identities" ) );

        assertEquals( "0.17.1", root.getModel()
                                    .getDependencyManagement()
                                    .getDependencies()
                                    .stream()
                                    .filter( d -> d.getArtifactId().equals( "atlas-identities" ) )
                                    .findFirst()
                                    .orElseThrow(Exception::new)
                                    .getVersion() );

        bs.inlineProperty( root, SimpleProjectRef.parse( "org.apache.maven:*" ) );

        // Demonstrate wildcard inlining of properties
        assertEquals( 0, root.getModel()
                                    .getDependencyManagement()
                                    .getDependencies()
                                    .stream()
                                    .filter( d -> d.getGroupId().equals( "org.apache.maven" ) )
                                    .filter( d -> d.getVersion().contains( "$" ) )
                                    .count() );

        bs.inlineProperty( root, "pmeVersion" );

        assertFalse( root.getModel()
                         .getDependencyManagement()
                         .getDependencies()
                         .stream()
                         .filter( d -> d.getArtifactId().equals( "pom-manipulation-common" ) )
                         .findFirst()
                         .orElseThrow(Exception::new)
                         .getVersion()
                         .contains( "$" ) );
    }
}
