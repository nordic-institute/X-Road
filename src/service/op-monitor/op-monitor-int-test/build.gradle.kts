plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

dependencies {
  intTestImplementation(project(path = ":service:op-monitor:op-monitor-db", configuration = "changelogJar"))
  "intTestImplementation"(project(":service:op-monitor:op-monitor-application"))
  "intTestImplementation"(project(":service:op-monitor:op-monitor-client"))
  "intTestImplementation"(project(":common:common-int-test"))
  intTestImplementation(libs.liquibase.core)
}

tasks.register<Test>("intTest") {
  dependsOn(":service:op-monitor:op-monitor-application:shadowJar")

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()

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

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}
