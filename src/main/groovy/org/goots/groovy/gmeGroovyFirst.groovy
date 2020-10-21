package org.goots.groovy

import org.commonjava.maven.ext.common.ManipulationException

/**
 * Example groovy file to manipulate a project.
 *
 */

import org.commonjava.maven.ext.common.ManipulationUncheckedException
import org.commonjava.maven.ext.core.groovy.GMEBaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
// Use BaseScript annotation to set script for evaluating the DSL.
@GMEBaseScript BaseScript gmeScript

if (!gmeScript.getInvocationStage()) throw new ManipulationException("Run this script via GME")

println "Running Groovy script on " + gmeScript.getBaseDir()

try {
    gmeScript.getModel().setName("newRoot")
    // No exception thrown - that is an error
    throw new RuntimeException("No exception thrown")
}
catch (ManipulationUncheckedException e )
{
    System.out.println ("PASS : Caught " + e.getMessage())
}

File information = new File(gmeScript.getBaseDir(), "build.gradle")
def newContent = information.text.replace('2.0.12.Final', "2.0.15.Final")
information.text = newContent

println "New content is " + newContent
