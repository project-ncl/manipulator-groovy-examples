package org.goots.groovy

import com.google.common.io.Files
import groovy.transform.CompileDynamic
import org.apache.maven.model.Dependency
import org.commonjava.maven.ext.common.ManipulationException
import org.commonjava.maven.ext.common.model.Project
import org.commonjava.maven.ext.common.util.PropertyResolver
import org.commonjava.maven.ext.core.groovy.BaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.commonjava.maven.ext.core.groovy.PMEBaseScript

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme

if (!pme.getInvocationStage()) throw new ManipulationException("Run this script via PME")

final productGroupId = 'com.redhat.quarkus'
final qBomArtifactId = 'quarkus-bom'
final platformArtifactId = 'quarkus-universe-bom'
final platformDeploymentArtifactId = 'quarkus-universe-bom-deployment'
final centralUrl = "https://repo1.maven.org/maven2/io/quarkus/$qBomArtifactId"
final String quarkusV = pme.getGAV().getVersionString()

def bomProject = pme.getPomIO().parseProject(pme.getFileIO().resolveURL("${centralUrl}/${quarkusV}/${qBomArtifactId}-${quarkusV}.pom")).get(0)
def runtimeBomProject = pme.projects.find { it.artifactId == platformArtifactId }
def runtimeBom = runtimeBomProject.model
def deploymentBomProject = pme.projects.find { it.artifactId == platformDeploymentArtifactId }
def deploymentBom = deploymentBomProject.model
def bomDirectory = 'bom/product-bom'

Project newProject = createProductOnlyBom("${bomDirectory}/runtime", runtimeBomProject, pme, bomProject, productGroupId, 'quarkus-product-bom')
Project newDeploymentProject = createProductOnlyBom("${bomDirectory}/deployment", deploymentBomProject, pme, bomProject, productGroupId, 'quarkus-product-bom-deployment')

runtimeBom.dependencyManagement.dependencies.add(importDep(newProject, pme))
deploymentBom.dependencyManagement.dependencies.add(importDep(newDeploymentProject, pme))


@CompileDynamic
private static Project createProductOnlyBom(String bomDirectory, Project runtimeBomProject, BaseScript pme, bomProject, String productGroupId, String bomName) {

    File productBomDir = new File(pme.getBaseDir(), bomDirectory)
    productBomDir.mkdirs()
    File productBom = new File(productBomDir, "pom.xml")
    Files.copy(runtimeBomProject.pom, productBom)

    pme.project.model.modules.add(bomDirectory)
    def newProject = pme.getPomIO().parseProject(productBom).get(0)

    pme.projects.add(newProject)

    newProject.model.dependencyManagement.dependencies.clear()
    bomProject.model?.dependencyManagement?.dependencies?.collect {
        dep ->
            def result = dep.clone()
            result.version = PropertyResolver.resolvePropertiesUnchecked(pme.session, [bomProject], dep.version)
            result
    }?.each {
        newProject.model.dependencyManagement.dependencies.add(it)
    }

    newProject.model.groupId = productGroupId
    newProject.model.artifactId = bomName
    newProject
}


private static Dependency importDep(Project project, BaseScript pme) {
    new Dependency(groupId: PropertyResolver.resolvePropertiesUnchecked(pme.session, project.getInheritedList(), project.groupId),
            artifactId:  PropertyResolver.resolvePropertiesUnchecked(pme.session, project.getInheritedList(), project.artifactId),
            scope: 'import',
            type: 'pom',
            version:  PropertyResolver.resolvePropertiesUnchecked(pme.session, project.getInheritedList(), project.version))
}
