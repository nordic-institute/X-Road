plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

sourceSets {
  main {
    java {
      srcDirs("src/main/java", schemaTargetDir)
    }
    resources {
      srcDirs("../../common/common-domain/src/main/resources")
    }
  }
}

configurations {
  create("xjc")
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  api(project(":common:common-domain"))

  implementation(libs.mapstruct)

  "xjc"(libs.bundles.jaxb)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.jupiter.params)
}

tasks.register("createDirs") {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.register("xjc") {
  description = "Generate Java classes from XSD schemas"
  group = "build"

  inputs.files(fileTree("src/main/resources") {
    include("*.xsd")
  })
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to configurations["xjc"].asPath
      )
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.ocspnextupdateparameters",
        "schema" to "src/main/resources/ocsp-nextupdate-conf.xsd"
      )
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.ocspfetchintervalparameters",
        "schema" to "src/main/resources/ocsp-fetchinterval-conf.xsd"
      )
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.monitoringparameters",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/monitoring-conf.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )
      // Generate classes for federateable global external conf v2
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.sharedparameters.v2",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v2/shared-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )

      // Generate classes for federateable global internal conf v2
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.privateparameters.v2",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v2/private-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )

      // Generate classes for federateable global external conf v3
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.sharedparameters.v3",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v3/shared-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )

      // Generate classes for federateable global internal conf v3
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.privateparameters.v3",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v3/private-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )

      // Generate classes for federateable global external conf v4
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.sharedparameters.v4",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v4/shared-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )

      // Generate classes for federateable global external conf v5
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.globalconf.schema.sharedparameters.v5",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/globalconf/v5/shared-parameters.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )
    }
  }
}

tasks.named("xjc") {
  dependsOn("createDirs", "processResources")
  mustRunAfter("processResources")
}

tasks.compileJava {
  dependsOn("xjc", "processResources")
}
