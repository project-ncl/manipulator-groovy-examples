
[![Build Status (Travis CI)](https://travis-ci.org/project-ncl/manipulator-groovy-examples.svg?branch=master)](https://travis-ci.org/project-ncl/manipulator-groovy-examples.svg?branch=master)


<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
    - [IntelliJ](#intellij)
- [Usage](#usage)
- [Contributions](#contributions)

<!-- markdown-toc end -->


Introduction
============

This is a demonstration Maven based project to create Groovy scripts for either
[PME](https://github.com/release-engineering/pom-manipulation-ext) or [GME](https://github.com/project-ncl/gradle-manipulator).
It is setup to make development easier within an IDE (primarily for IntelliJ but should work in other environments).

Prerequisites
=============

IntelliJ
--------

To ensure the [GMavenPlus](https://github.com/groovy/GMavenPlus/) plugin correctly works within
IntelliJ it is advised that the following plugin is installed first:

* https://plugins.jetbrains.com/plugin/7442-gmavenplus-intellij-plugin

It is also recommended to install:

* https://plugins.jetbrains.com/plugin/6317-lombok/

Usage
=====

Currently this project comes with two sample groovy scripts ( with API completion enabled ) and two
sample tests to demonstrate the application of those scripts. The project should be imported into IntelliJ
as a new project and it should use the Maven model it setup the structure.

For a breakdown of existing scripts please see [this](https://github.com/project-ncl/manipulator-groovy-examples/blob/master/SCRIPT_INDEX.md)

**Note:** Do not attempt to run the Groovy scripts directly as that will not work or demonstrate usage of the tooling. Instead, run them via the example tests.

**Note:** it is currently not possible to debug a GME script with invocation point `LAST` when invoking the tool via the CLI. See [here](https://project-ncl.github.io/gradle-manipulator/guide/groovy.html#developing-groovy-scripts) for further information.

Contributions
=============

Contributions are very welcome. Each example Groovy script should be paired with a test and documentation to describe its usage.
