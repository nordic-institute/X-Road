plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":security-server:openapi-model"))
  intTestImplementation(project(":service::proxy:proxy-monitoring-api"))
  intTestImplementation(project(":service:op-monitor:op-monitor-core"))

  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.testAutomation.assert)
  intTestImplementation(libs.feign.hc5)
  intTestImplementation(libs.postgresql)
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "MESSAGELOG_INIT_IMG" to "ss-db-messagelog-init",
    "OP_MONITOR_INIT_IMG" to "ss-db-opmonitor-init",
    "CONFIGURATION_CLIENT_IMG" to "ss-configuration-client",
    "MONITOR_IMG" to "ss-monitor",
    "SIGNER_IMG" to "ss-signer",
    "PROXY_IMG" to "ss-proxy",
    "PROXY_UI_IMG" to "ss-proxy-ui-api",
    "BACKUP_MANAGER_IMG" to "ss-backup-manager",
    "OP_MONITOR_IMG" to "ss-op-monitor"
  )
}

val copyMainComposeFile by tasks.registering(Copy::class) {
  description = "Copies main compose.yaml and required files to build directory"
  group = "verification"

  from("../../../development/docker/security-server/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  into("build/resources/intTest")
}

tasks.register<Test>("systemTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(copyMainComposeFile)

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
