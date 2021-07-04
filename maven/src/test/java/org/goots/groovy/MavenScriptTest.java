package org.goots.groovy;

import com.google.common.io.Resources;
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
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MavenScriptTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Test
    public void testApplyingPMEScript() throws Exception
    {
        final URL groovy = Resources.getResource( "pmeBasicDemo.groovy" );
        final File folder = temp.newFolder();
        final File mvnRepo = temp.newFolder();
        final File rootPom = new File( folder.getCanonicalFile(), "pom.xml" );

        Git.cloneRepository().setURI( "https://github.com/michalszynkiewicz/empty.git" ).setDirectory( folder ).call();
        System.out.println( "Cloned to " + folder );

        Properties prop = new Properties();
        prop.setProperty( "versionSuffix", "release-1" );
        prop.setProperty( "groovyScripts", groovy.toString() );
        prop.setProperty( "maven.repo.local", mvnRepo.toString() );
        SMContainer smc = TestUtils.createSessionAndManager( prop );
        smc.getRequest().setPom( rootPom );
        smc.getManager().scanAndApply( smc.getSession() );

        assertTrue( systemOutRule.getLog().contains( "BASESCRIPT" ) );
        assertTrue( systemOutRule.getLog().contains( "Project version : 1.0-SNAPSHOT --> 1.0.0.release-1" ) );
    }

    @Test
    public void testQuarkusGroovyAnnotation() throws Exception
    {
        final URL preGroovy = Resources.getResource( "quarkusPlatformPre.groovy" );
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
        prop.setProperty( "groovyScripts", preGroovy.toString() );
        prop.setProperty( "maven.repo.local", mvnRepo.toString() );
        SMContainer smc = TestUtils.createSessionAndManager( prop );
        smc.getRequest().setPom( rootPom );
        smc.getManager().scanAndApply( smc.getSession() );

        List<Project> result = new PomIO().parseProject( rootPom );
        assertTrue( new File( quarkusFolder, "bom" + File.separator + "product-bom" ).exists() );
        assertEquals( 5, result.get( 0 ).getModel().getModules().size() );
        assertEquals( result.stream().filter( p -> p.getArtifactId().equals( "quarkus-product-bom" ) )
                            .map( pr -> pr.getModel().getDependencyManagement().getDependencies().size() ).findFirst().orElse( 0 ),
                      Integer.valueOf( 445 ) );
    }

    @Test
    public void testGroovyAnnotation() throws Exception {
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
        final URL groovy = Resources.getResource("pmeBasicDemo.groovy");
        final File groovyFile = Paths.get(groovy.toURI()).toFile();
        TestUtils.executeMethod(gm, "applyGroovyScript", new Class[]{List.class, Project.class, File.class},
                new Object[]{projects, root, groovyFile});
        assertTrue( systemOutRule.getLog().contains( "BASESCRIPT") );

        List<String> result = projects.get(0).getModel().getDependencies().stream().
                filter(d -> d.getGroupId().equals("org.apache.maven") && d.getArtifactId().equals("maven-core")).
                map(Dependency::getVersion).collect(Collectors.toList());

        assertEquals(1, result.size());
        assertNull(result.get(0));
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

	@Test
	public void testPMEScriptManipulations() throws Exception {
		final URL groovy = Resources.getResource("manipulatePom.groovy");
		final File registry = temp.newFolder();
		final File mvnRepo = temp.newFolder();
		final File rootPom = new File(registry.getCanonicalFile(), "pom.xml");

		Git.cloneRepository()
				.setURI("https://github.com/Apicurio/apicurio-registry.git")
				.setDirectory(registry)
				.setBranch("refs/tags/2.0.1.Final")
				.call();
		System.out.println("Cloned to " + registry);

		// Before manipulations
		List<Project> projects = new PomIO().parseProject(rootPom);

		// 1. Check that the version property is not inlined for 'quarkus-test-common'
		assertEquals("${quarkus.version}", projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("io.quarkus") && d.getArtifactId().equals("quarkus-test-common"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getVersion());
		
		
		// 2. Fetch the value of property 'quarkus.version'
		assertEquals("1.12.2.Final", projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst().orElseThrow(Exception::new)
				.getModel()
				.getProperties()
				.getProperty("quarkus.version"));
		
		
		// 3. Check if groupId 'com.github.everit-org.json-schema' exists and groupId 'org.everit.json' doesn't
		assertEquals(1, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("com.github.everit-org.json-schema"))
				.count());		
		assertEquals(0, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("org.everit.json"))
				.count());
		
		
		// 5. Check that the mockito dependency is not present in the module 'apicurio-registry-app'
		assertEquals(0, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry-app"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencies().stream()
				.filter(d -> d.getGroupId().equals("org.mockito") && d.getArtifactId().equals("mockito-core") && d.getVersion().equals("3.11.2"))
				.count());
		
		
		// 6. Check that the jackson-databind dependency is not present in the <dependencyManagement> of root pom
		assertEquals(0, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("com.fasterxml.jackson.core") && d.getArtifactId().equals("jackson-databind"))
				.count());
		
		
		// Configuring and running PME
		Properties prop = new Properties();
		prop.setProperty("groovyScripts", groovy.toString());
		prop.setProperty("maven.repo.local", mvnRepo.toString());
		SMContainer smc = TestUtils.createSessionAndManager(prop);
		smc.getRequest().setPom(rootPom);
		smc.getManager().scanAndApply(smc.getSession());

		// After Manipulations
		projects = new PomIO().parseProject(rootPom);

		// 1. asserting that the version is inlined for 'quarkus-test-common'
		assertTrue(systemOutRule.getLog().contains("Custom Adjustments : Inlining version for io.quarkus:quarkus-test-common"));
		assertEquals("1.12.2.Final", projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("io.quarkus") && d.getArtifactId().equals("quarkus-test-common"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getVersion());
		
		
		
		// 2. Asserting that the value of property 'quarkus.version' is overridden 
		assertTrue(systemOutRule.getLog().contains("Custom Adjustments : Overriding value of the property 'quarkus.version'"));
		assertEquals("1.11.7.Final-redhat-00009", projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst().orElseThrow(Exception::new)
				.getModel()
				.getProperties()
				.getProperty("quarkus.version"));
		
		
		
		// 3. Assert that the groupId 'com.github.everit-org.json-schema' is overridden
		assertTrue(systemOutRule.getLog().contains("Custom Adjustments : Overriding groupId: 'com.github.everit-org.json-schema' ---> org.everit.json"));
		assertEquals(0, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("com.github.everit-org.json-schema"))
				.count());		
		assertEquals(1, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("org.everit.json"))
				.count());
		
		
		
		// 4. Assert that deployment is enabled for the module 'apicurio-registry-storage-kafkasql'
		assertTrue(systemOutRule.getLog().contains("Deployment Enabled for module io.apicurio:apicurio-registry-storage-kafkasql"));
		
		
		
		// 5. Assert that the mockito dependency is added to the module 'apicurio-registry-app'
		assertEquals(1, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry-app"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencies().stream()
				.filter(d -> d.getGroupId().equals("org.mockito") && d.getArtifactId().equals("mockito-core") && d.getVersion().equals("3.11.2"))
				.count());
		
		
		
		// 6. Assert that the jackson-databind dependency is added in the <dependencyManagement> of root pom
		assertEquals(1, projects.stream()
				.filter(p -> p.getArtifactId().equals("apicurio-registry"))
				.findFirst()
				.orElseThrow(Exception::new)
				.getModel()
				.getDependencyManagement().getDependencies().stream()
				.filter(d -> d.getGroupId().equals("com.fasterxml.jackson.core") && d.getArtifactId().equals("jackson-databind"))
				.count());
	}
	
}
