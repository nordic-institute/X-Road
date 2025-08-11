plugins {
  id("xroad.java-conventions")
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

val xjc by configurations.creating

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
    resources.srcDirs("../../../common/common-domain/src/main/resources")
  }
}

dependencies {
  api(platform(libs.springBoot.bom))

  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(libs.jakarta.validationApi)
  implementation(libs.bundles.metrics)
  implementation(libs.mapstruct)

  implementation(project(":common:common-domain"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-jetty"))
  implementation(project(":common:common-db"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":service:op-monitor:op-monitor-api"))

  api("org.springframework:spring-context-support")

  testImplementation(libs.hsqldb)
  testImplementation(project(":common:common-test"))
  testImplementation(libs.commons.cli)

  xjc(libs.bundles.jaxb)
}

val createDirs by tasks.registering {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.register("xjc") {
  description = "Generate Java classes from XSD schema"
  group = "build"

  inputs.files(fileTree("src/main/resources").include("*.xsd"))
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to xjc.asPath
      )

      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.opmonitordaemon.message",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/op-monitoring.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )
    }
  }
}

tasks.named("xjc") {
  dependsOn(createDirs)
  dependsOn(tasks.processResources)
  mustRunAfter(tasks.processResources)
}

tasks.compileJava {
  dependsOn(tasks.named("xjc"))
  dependsOn(tasks.processResources)
}
