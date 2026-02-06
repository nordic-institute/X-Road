plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

val xjc by configurations.creating

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

sourceSets {
  main {
    java.srcDirs("src/main/java", schemaTargetDir)
  }
}

dependencies {
  api(project(":common:common-core"))

  testImplementation(project(":common:common-message"))
  testImplementation(project(":common:common-test"))

  xjc(libs.bundles.jaxb)
}

tasks.register("xjc") {
  description = "Generate JAXB classes from XSD schemas"
  group = "build"

  inputs.files(fileTree("src/main/resources") { include("*.xsd") })
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to xjc.asPath
      )

      // Generate classes for identifiers
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.identifier",
        "schema" to "src/main/resources/identifiers.xsd"
      )

      // Generate classes for message, using identifier classes
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.message",
        "schema" to "src/main/resources/message.xsd",
        "binding" to "src/main/resources/identifiers-bindings.xml"
      )

      // Generate classes for request, using identifier classes
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.request",
        "schema" to "src/main/resources/request.xsd",
        "binding" to "src/main/resources/identifiers-bindings.xml"
      )

      // Generate classes for service metainfo
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.metadata",
        "schema" to "src/main/resources/service-metainfo.xsd",
        "binding" to "src/main/resources/identifiers-bindings.xml"
      )

      // DEMO: Generate classes for identifiers-v2 with xs:choice (terminology transition demo)
      // Uses xjc:superInterface to make generated classes implement XRoadIdentifier
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.identifier.v2",
        "schema" to "src/main/resources/identifiers-v2-demo.xsd",
        "binding" to "src/main/resources/identifiers-v2-bindings.xml",
        "extension" to "true"
      )

      // DEMO: Generate v4 legacy classes (old terminology: memberClass, xRoadInstance)
      // Uses xjc:superInterface to make generated classes implement IdentifierCommon
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.identifier.v4",
        "schema" to "src/main/resources/identifiers-v4-legacy.xsd",
        "binding" to "src/main/resources/identifiers-v4-bindings.xml",
        "extension" to "true"
      )

      // DEMO: Generate v5 new classes (new terminology: participantClass, dataspaceInstance)
      // Uses xjc:superInterface to make generated classes implement IdentifierCommon
      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.identifier.v5",
        "schema" to "src/main/resources/identifiers-v5-new.xsd",
        "binding" to "src/main/resources/identifiers-v5-bindings.xml",
        "extension" to "true"
      )
    }
  }
}

tasks.compileJava {
  dependsOn(tasks.named("xjc"))
}
