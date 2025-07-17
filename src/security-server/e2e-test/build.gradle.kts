plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.testAutomation.assert)
  intTestImplementation(libs.testAutomation.restassured)
  intTestImplementation(libs.feign.hc5)
}

tasks.register<Test>("e2eTest") {
  useJUnitPlatform()

  description = "Runs e2e tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val systemTestArgs = mutableListOf("-XX:MaxMetaspaceSize=200m")

  if (project.hasProperty("e2eTestServeReport")) {
    systemTestArgs += "-Dtest-automation.report.allure.serve-report.enabled=${project.property("e2eTestServeReport")}"
  }
  if (project.hasProperty("e2eTestCSImage")) {
    systemTestArgs += "-Dtest-automation.custom.cs-image=${project.property("e2eTestCSImage")}"
  }
  if (project.hasProperty("e2eTestSSImage")) {
    systemTestArgs += "-Dtest-automation.custom.ss-image=${project.property("e2eTestSSImage")}"
  }
  if (project.hasProperty("e2eTestCAImage")) {
    systemTestArgs += "-Dtest-automation.custom.cs-image=${project.property("e2eTestCAImage")}"
  }
  if (project.hasProperty("e2eTestISOPENAPIImage")) {
    systemTestArgs += "-Dtest-automation.custom.isopenapi-image=${project.property("e2eTestISOPENAPIImage")}"
  }
  if (project.hasProperty("e2eTestISSOAPImage")) {
    systemTestArgs += "-Dtest-automation.custom.issoap-image=${project.property("e2eTestISSOAPImage")}"
  }
  if (project.hasProperty("e2eTestUseCustomEnv")) {
    systemTestArgs += "-Dtest-automation.custom.use-custom-env=${project.property("e2eTestUseCustomEnv")}"
  }

  jvmArgs(systemTestArgs)

  maxHeapSize = "256m"

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
