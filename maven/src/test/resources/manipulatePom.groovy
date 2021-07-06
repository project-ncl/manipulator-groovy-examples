import groovy.util.logging.Slf4j
import org.apache.maven.model.Build
import org.apache.maven.model.Dependency
import org.apache.maven.model.DependencyManagement
import org.apache.maven.model.Plugin
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef
import org.commonjava.maven.ext.common.ManipulationException
import org.commonjava.maven.ext.common.model.Project
import org.commonjava.maven.ext.core.groovy.BaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.commonjava.maven.ext.core.groovy.PMEBaseScript
import org.commonjava.maven.ext.core.ManipulationSession
import org.commonjava.maven.ext.core.state.RESTState
import org.commonjava.maven.ext.io.rest.Translator
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef


@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme
@Slf4j
public class MangleVersion {

    BaseScript pme;


    def execute() {

        log.info "##########################################################################################################################################"
        // Manipulation 1
        // Quarkus doesn't productize test dependencies
        // Inlining property version to use community version of quarkus-test-common
        log.info "--------------------------------------------------------------------------------------------------------"
        log.info "Custom Adjustments : Inlining version for io.quarkus:quarkus-test-common"
        log.info "--------------------------------------------------------------------------------------------------------"
        for ( Project p : pme.getProjects() ) {
            pme.inlineProperty(pme.getProject(), SimpleProjectRef.parse("io.quarkus:quarkus-test-common"))
        }
        log.info "---------------------------------------- Inlining Complete ---------------------------------------------"
        log.info ""
		
		// Manipulation 2
		// Sometimes we might want to override a property
        // This methods helps in overriding the value of a specific property 
        overrideProperty("quarkus.version", "1.11.7.Final-redhat-00009")

        pme.projects.each {
            project ->
                def deps = [project.model?.dependencies, project.model?.dependencyManagement?.dependencies].flatten() //aggregate the 2 sets in a single list
                deps.removeAll { it == null } // remove nulls
                
                log.info " # Scanning Module =>  $project.groupId:$project.artifactId"
				
				// Manipulation 3
	            // artifact 'org.everit.json.schema' is available from group 'org.everit.json' in Indy
	            // Hence, in situations like this we need to override the groupId for a specific artifact
                overrideGroupId(deps, "com.github.everit-org.json-schema", "org.everit.json")                
				
				// Manipulation 4
				// sometime deployment for a specific module is disabled for a specific module in upstream
				// But we might want to deploy that module in downstream
				// This method will check if the deploy plugin is configured to skip deploy. If yes, it will configure the plugin to deploy the module.
                enableDeployForSpecificModule(project, "io.apicurio", "apicurio-registry-storage-kafkasql")
				
				// Manipulation 5
				// Sometimes we might want to use a specific version of a dependency in a particular module to avoid build/compilation failure
                // This method adds the specified dependency in the module specified        
                if (project.groupId == "io.apicurio" && project.artifactId == "apicurio-registry-app") {
                    addDependency(project, "org.mockito", "mockito-core", "3.11.2", "test")
                }
				
				// Manipulation 6
				// Sometime we might want to make a specific version of a dependency available to all the modules
                // This method adds a new dependency in the <dependencyManagement> of root pom
                if (project.groupId == "io.apicurio" && project.artifactId == "apicurio-registry") {
                    addDependencyInDependencyManagemnt(project, "com.fasterxml.jackson.core", "jackson-databind", "2.11.0", "compile")
                }
        }
        log.info "##########################################################################################################################################"
    }

    def overrideProperty(String propertyName, String newValue) {
        log.info "--------------------------------------------------------------------------------------------------------"
        log.info "Custom Adjustments : Overriding value of the property '$propertyName'"
        log.info "--------------------------------------------------------------------------------------------------------"
        String existingValue = pme.project.model.properties.get(propertyName)
        pme.getProject().getModel().getProperties().setProperty(propertyName, newValue) // overriding the property value
        String overriddenValue = pme.project.model.properties.get(propertyName)       
        log.info "Old Value: $existingValue"
        log.info "New Value: $overriddenValue"
        log.info "-------------------------------------- Property Value Overriden -----------------------------------------"        
        log.info ""
    }

    def overrideGroupId(deps, String oldGroup, String newGroup) {
        deps.each {
            dep ->  
                log.debug "$dep"
                if (dep.groupId == oldGroup) {                     
                     log.info "--------------------------------------------------------------------------------------------------------"
                     log.info "Custom Adjustments : Overriding groupId: '$oldGroup' ---> $newGroup"
                     log.info "--------------------------------------------------------------------------------------------------------"
                     log.info "Old Coordinates: $dep"                            
                     dep.groupId = newGroup
                     log.info "New Coordinates: $dep"
                     log.info "------------------------------------------ GroupId Overriden -------------------------------------------"                     
        	     log.info ""   
                }
        }
         
    }

    def addDependency(project, String groupId, String artifactId, String version, String scope) {        
        log.info "--------------------------------------------------------------------------------------------------------"
        log.info "Custom Adjustments : Adding a new Dependency"
        log.info "--------------------------------------------------------------------------------------------------------"
        def dep = new Dependency()
        dep.groupId = groupId
        dep.artifactId = artifactId
        dep.version = version
        dep.scope = scope
        project.model.dependencies.add(dep)
        log.info "$groupId:$artifactId:$version:$scope ---> in project '$project'"        
        log.info "------------------------------------------ Dependency Added --------------------------------------------"        
        log.info ""
    }

    def addDependencyInDependencyManagemnt(project, String groupId, String artifactId, String version, String scope) {
        log.info "--------------------------------------------------------------------------------------------------------"
        log.info "Custom Adjustments : Added a new Dependency in DependencyManagement"
        log.info "--------------------------------------------------------------------------------------------------------"
        def dep = new Dependency()
        dep.groupId = groupId
        dep.artifactId = artifactId
        dep.version = version
        dep.scope = scope
        project.model.dependencyManagement.dependencies.add(dep)
        log.info "$groupId:$artifactId:$version:$scope ---> in project '$project'"        
        log.info "------------------------------------------ Dependency Added --------------------------------------------"        
        log.info ""
        
    }

    def enableDeployForSpecificModule (project, String groupId, String artifactId) {
        
        if (project.groupId == groupId && project.artifactId == artifactId) {
            log.info "--------------------------------------------------------------------------------------------------------"
            log.info "Custom Adjustments : Enabling Deploy for module $groupId:$artifactId"
            log.info "--------------------------------------------------------------------------------------------------------"
            if (project.model.build == null){
                log.info "Creating build"
                project.model.build = new Build()
                }

            if (project.model.build.plugins == null){
                log.info "Creating plugins"
                project.model.build.plugins = new ArrayList<Plugin>()
            }

            def plugin = project.model.build.plugins.find {
                it.groupId == "org.apache.maven.plugins" &&
                it.artifactId == "maven-deploy-plugin"
            }
            if (plugin == null){
                log.info "Plugin not found"
                plugin = new Plugin()
                plugin.groupId = "org.apache.maven.plugins"
                plugin.artifactId = "maven-deploy-plugin"
                project.model.build.plugins.add(plugin)
            }
            log.info "Plugin: $plugin"
            if (plugin.configuration == null) {
                log.info "Creating configuration"
                def configuration = new Xpp3Dom("configuration")
                def skip = new Xpp3Dom("skip")
                skip.value = "false"
                configuration.addChild(skip)
                plugin.configuration = configuration
            } else {
                log.info "Plugin configuration not empty"
                def config = plugin.configuration as Xpp3Dom
                def skip = config.getChild("skip")
                if (skip == null){
                    log.info "Modifying configuration"
                    skip = new Xpp3Dom("skip")
                    config.addChild(skip)
                }
                else if (skip.value == "true") {
                    log.info "existing skip value is '$skip.value'. Thus, the module won't get deployed."
                    log.info "Modifying the skip value to 'false'"
                    skip.value = "false"
                }
                else {
                    log.info "existing skip value is '$skip.value'. Thus, the module will get deployed."
                }
            }
        log.info "------------------------- Deployment Enabled for module $groupId:$artifactId -------------------------"
        log.info ""
        }
    }


}

MangleVersion mangleVersion = new MangleVersion(pme: pme)
mangleVersion.execute()
