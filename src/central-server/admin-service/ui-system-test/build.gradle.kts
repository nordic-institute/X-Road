plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":central-server:openapi-model"))
  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.bundles.testAutomation)
  intTestImplementation(libs.testAutomation.selenide) {
    exclude(group = "org.slf4j", module = "*")
  }
  intTestImplementation(libs.bouncyCastle.bcpkix)
  intTestImplementation(libs.awaitility)
}

tasks.register<Test>("systemTest") {
  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val systemTestArgs = mutableListOf<String>()

  if (project.hasProperty("systemTestTags")) {
    systemTestArgs += "-Dtest-automation.cucumber.filter.tags=${project.property("systemTestTags")}"
  }
  if (project.hasProperty("systemTestServeReport")) {
    systemTestArgs += "-Dtest-automation.report.allure.serve-report.enabled=${project.property("systemTestServeReport")}"
  }
  if (project.hasProperty("systemTestCentralServerUrl")) {
    systemTestArgs += "-Dtest-automation.custom.central-server-url-override=${project.property("systemTestCentralServerUrl")}"
  }
  if (project.hasProperty("systemTestCsImageName")) {
    systemTestArgs += "-Dtest-automation.custom.image-name=${project.property("systemTestCsImageName")}"
  }

  jvmArgs(systemTestArgs)

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }
}

archUnit {
  setSkip(true)
}
