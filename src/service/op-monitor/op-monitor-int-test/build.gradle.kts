plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.shadow)
  alias(libs.plugins.allure)
}

dependencies {
  intTestImplementation(project(path = ":service:op-monitor:op-monitor-db", configuration = "changelogJar"))
  intTestImplementation(project(":service:op-monitor:op-monitor-client"))
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(libs.liquibase.core)

  intTestRuntimeOnly(libs.postgresql)
}

intTestComposeEnv {
  images(
    "OP_MONITOR_INIT_IMG" to "ss-db-opmonitor-init",
    "OP_MONITOR_IMG" to "ss-op-monitor"
  )
}

tasks.register<Test>("intTest") {
  dependsOn(":service:op-monitor:op-monitor-application:quarkusBuild")
  dependsOn(provider { tasks.named("generateIntTestEnv") })

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }

}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  archiveBaseName.set("opmonitor-int-test")
  archiveClassifier.set("")
  isZip64 = true

  from(sourceSets["intTest"].output.classesDirs)

  from("${layout.buildDirectory.get().asFile}/resources/intTest") {
    into("")
  }
  from("${layout.buildDirectory.get().asFile}/resources/intTest/.env") {
    into("")
  }
  from(sourceSets["intTest"].runtimeClasspath.filter { it.name.endsWith(".jar") })

  mergeServiceFiles()
  exclude("**/module-info.class")

  manifest {
    attributes(
      "Main-Class" to "org.niis.xroad.opmonitor.test.ConsoleIntTestRunner"
    )
  }

  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(tasks.named("intTestClasses"))
  dependsOn(tasks.named("processIntTestResources"))
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
archUnit {
  setSkip(true)
}
