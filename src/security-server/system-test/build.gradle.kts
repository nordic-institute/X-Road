plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":security-server:openapi-model"))
  intTestImplementation(project(":addons:proxymonitor-common"))
  intTestImplementation(project(":service:op-monitor:op-monitor-core"))

  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.testAutomation.assert)
  intTestImplementation(libs.testAutomation.selenide) {
    exclude(group = "org.slf4j", module = "*")
  }
  intTestImplementation(libs.feign.hc5)
  intTestImplementation(libs.postgresql)
}

tasks.register<Test>("systemTest") {
  useJUnitPlatform()

  description = "Runs system ui tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val systemTestArgs = mutableListOf("-XX:MaxMetaspaceSize=200m")

  if (project.hasProperty("systemTestSsTags")) {
    systemTestArgs += "-Dtest-automation.cucumber.filter.tags=${project.property("systemTestSsTags")}"
  }
  if (project.hasProperty("systemTestSsServeReport")) {
    systemTestArgs += "-Dtest-automation.report.allure.serve-report.enabled=${project.property("systemTestSsServeReport")}"
  }
  if (project.hasProperty("systemTestSsImageName")) {
    systemTestArgs += "-Dtest-automation.custom.image-name=${project.property("systemTestSsImageName")}"
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
