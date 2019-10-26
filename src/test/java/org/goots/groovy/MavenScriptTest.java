package org.goots.groovy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.commonjava.maven.atlas.ident.ref.SimpleArtifactRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationManager;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.groovy.BaseScript;
import org.commonjava.maven.ext.core.impl.InitialGroovyManipulator;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import junit.framework.TestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MavenScriptTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testGroovyAnnotation() throws Exception {
        final File groovy = GroovyLoader.loadGroovy("pme.groovy");
        final File pRoot = new File(TestUtils.resolveFileResource("", "")
                .getParentFile()
                .getParentFile(), "pom.xml");
        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(pRoot);
        ManipulationManager m = new ManipulationManager(null, Collections.emptyMap(), Collections.emptyMap(), null);
        ManipulationSession ms = TestUtils.createSession(null);
        m.init(ms);

        Project root = projects.stream().filter(p -> p.getProjectParent()==null).findAny().orElse(null);

        InitialGroovyManipulator gm = new InitialGroovyManipulator(null, null);
        gm.init(ms);
        TestUtils.executeMethod(gm, "applyGroovyScript", new Class[]{List.class, Project.class, File.class},
                new Object[]{projects, root, groovy});
        assertTrue(systemRule.getLog().contains("BASESCRIPT"));

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
        ManipulationManager m = new ManipulationManager( null, Collections.emptyMap(), Collections.emptyMap(), null );
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
        bs.setValues( null, ms, projects, root, null );

        bs.inlineProperty( root, SimpleProjectRef.parse( "org.commonjava.maven.atlas:atlas-identities" ) );

        assertEquals( "0.17.1", root.getModel()
                                    .getDependencyManagement()
                                    .getDependencies()
                                    .stream()
                                    .filter( d -> d.getArtifactId().equals( "atlas-identities" ) )
                                    .findFirst()
                                    .orElseThrow(Exception::new)
                                    .getVersion() );

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
