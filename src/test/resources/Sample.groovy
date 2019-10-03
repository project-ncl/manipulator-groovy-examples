import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef
import org.commonjava.maven.ext.core.groovy.BaseScript
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.commonjava.maven.ext.core.groovy.PMEBaseScript
import org.commonjava.maven.ext.core.groovy.PMEInvocationPoint

@PMEInvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme

println "#### BASESCRIPT:"
println pme.getBaseDir()
println pme.getBaseDir().getClass().getName()
println pme.getGAV()
println pme.getGAV().getClass().getName()
println pme.getProjects()
println pme.getProject().getClass().getName()
println pme.getProjects()
println pme.getProject().getClass().getName()
println pme.getSession().getPom()
println "#### BASESCRIPT END"


pme.inlineProperty (pme.getProject(), SimpleProjectRef.parse("org.apache.maven:maven-core"))
