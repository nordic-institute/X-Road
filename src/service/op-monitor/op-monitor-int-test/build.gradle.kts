plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(path = ":service:op-monitor:op-monitor-db", configuration = "changelogJar"))
  intTestImplementation(project(":service:op-monitor:op-monitor-client"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-int-test"))
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

  reports {
    junitXml.required.set(false)
  }
}

archUnit {
  setSkip(true)
}
