
# Script Index

This contains a description of the current set of manipulation scripts.


## pmeBasicDemo

This script contains an example of some of the API as well as inlining a property 
within the model that would be written out to the `pom.xml` file. The corresponding 
test verifies this and further inlining behaviour.

## gmeBasicDemo

This script demonstrates modifying the current model (which results in changes to the json)
and modifying both the `build.gradle` file and the `settings.gradle` file. The corresponding
tests verify this.

## gmeGroovyFirst

This demonstrates running a Groovy script before invoking Gradle. It will modify the `build.gradle` file on disk.

## quarkusPlatformPre

This script demonstrates adding modules to a project, using the extended API and resolving POMs from external locations.