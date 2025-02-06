plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":central-server:admin-service:api-client"))
  intTestImplementation(testFixtures(project(":common:common-management-request")))
  intTestImplementation(libs.feign.hc5)

  intTestImplementation(libs.bundles.testAutomation) {
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
  }
  intTestImplementation(libs.testAutomation.assert)
}

tasks.register<Test>("intTest") {
  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()

  if (project.hasProperty("intTestTags")) {
    intTestArgs += "-Dtest-automation.cucumber.filter.tags=${project.property("intTestTags")}"
  }
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
    junitXml.required.set(false) // equivalent to includeSystemOutLog = false
  }

  shouldRunAfter(tasks.test)
}

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}

archUnit {
  setSkip(true)
}
