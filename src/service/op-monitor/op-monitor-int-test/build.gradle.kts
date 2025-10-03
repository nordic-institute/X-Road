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

tasks.register<Test>("intTest") {
  dependsOn(":service:op-monitor:op-monitor-application:quarkusBuild")

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()

  // TODO: make it configurable when fixing int tests
  intTestArgs += "-Dtest-automation.custom.image-name=localhost:5555/ss-op-monitor:latest"

  if (project.hasProperty("intTestProfilesInclude")) {
    intTestArgs += "-Dspring.profiles.include=${project.property("intTestProfilesInclude")}"
  }

  jvmArgs(intTestArgs)

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
