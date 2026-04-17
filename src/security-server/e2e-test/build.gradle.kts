import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(libs.test.restassured)
  intTestImplementation(libs.postgresql)
  intTestImplementation(project(":lib:asic-core"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":lib:globalconf-impl"))
  intTestImplementation(project(":lib:vault-core"))
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "CS_IMG" to "central-server-dev",
    "POSTGRES_DEV_IMG" to "postgres-dev",
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "DB_INIT_IMG" to "ss-db-init",
    "CONFIGURATION_CLIENT_IMG" to "ss-configuration-client",
    "MONITOR_IMG" to "ss-monitor",
    "SIGNER_IMG" to "ss-signer",
    "SOFTTOKEN_SIGNER_IMG" to "ss-softtoken-signer",
    "PROXY_IMG" to "ss-proxy",
    "PROXY_UI_IMG" to "ss-proxy-ui-api",
    "AUXILIARY_SERVICE_IMG" to "ss-auxiliary-service",
    "OP_MONITOR_IMG" to "ss-op-monitor",
    "CA_IMG" to "testca-dev",
    "MESSAGE_LOG_ARCHIVER_IMG" to "ss-message-log-archiver",
    "DS_CONTROL_PLANE_IMG" to "ds-control-plane",
    "DS_DATA_PLANE_IMG" to "ds-data-plane",
    "DS_IDENTITY_HUB_IMG" to "ds-identity-hub",
    "DS_ISSUER_SERVICE_IMG" to "ds-issuer-service"
  )
}

intTestShadowJar {
  archiveBaseName("e2e-test")
  mainClass("org.niis.xroad.e2e.ConsoleE2ETestRunner")
}

val copyComposeFiles by tasks.registering(Copy::class) {
  description = "Copies compose files and related resources to build directory for e2e tests"
  group = "verification"

  from("../../../development/docker/security-server/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  from("../../../development/hurl") {
    into("hurl")
  }
  from("../../../development/docker/security-server/signer-with-hsm") {
    into("signer-with-hsm")
  }
  into("build/resources/intTest")
}

tasks.register<Test>("e2eTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(provider { tasks.named("copyComposeFiles") })
  useJUnitPlatform()

  description = "Runs e2e tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val systemTestArgs = mutableListOf("-XX:MaxMetaspaceSize=200m")

  if (project.hasProperty("e2eTestServeReport")) {
    systemTestArgs += "-Dtest-automation.report.allure.serve-report.enabled=${project.property("e2eTestServeReport")}"
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

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(provider { tasks.named("copyComposeFiles") })
}

tasks.named<ShadowJar>("shadowJar") {
  dependsOn(provider { tasks.named("copyComposeFiles") })
}

archUnit {
  setSkip(true)
}

