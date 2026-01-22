import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":security-server:openapi-model"))
  intTestImplementation(project(":service::proxy:proxy-monitoring-api"))
  intTestImplementation(project(":service:op-monitor:op-monitor-core")){
    exclude(group = "org.jboss.slf4j", module = "slf4j-jboss-logmanager")
  }
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":lib:globalconf-core"))
  intTestImplementation(libs.postgresql)

  testImplementation(project(":common:common-test"))
  testImplementation(project(":lib:globalconf-core"))
  // FIXME: move required classes to common
  testImplementation(project(":central-server:admin-service:core-api"))
  testImplementation(project(":central-server:admin-service:globalconf-generator"))
  testImplementation(project(":service:configuration-client:configuration-client-core"))
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "POSTGRES_DEV_IMG" to "postgres-dev",
    "CA_IMG" to "testca-dev",
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "MESSAGELOG_INIT_IMG" to "ss-db-messagelog-init",
    "OP_MONITOR_INIT_IMG" to "ss-db-opmonitor-init",
    "CONFIGURATION_CLIENT_IMG" to "ss-configuration-client",
    "MONITOR_IMG" to "ss-monitor",
    "SIGNER_IMG" to "ss-signer",
    "PROXY_IMG" to "ss-proxy",
    "PROXY_UI_IMG" to "ss-proxy-ui-api",
    "BACKUP_MANAGER_IMG" to "ss-backup-manager",
    "OP_MONITOR_IMG" to "ss-op-monitor",
    "DS_CONTROL_PLANE_IMG" to "ds-control-plane"
  )
}

intTestShadowJar {
  archiveBaseName("security-server-system-test")
  mainClass("org.niis.xroad.ss.test.ConsoleSystemTestRunner")
}

val copyMainComposeFile by tasks.registering(Copy::class) {
  description = "Copies main compose.yaml and required files to build directory"
  group = "verification"

  from("../../../development/docker/security-server/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  into("build/resources/intTest")
}

tasks.register<Test>("intTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(copyMainComposeFile)

  useJUnitPlatform()

  description = "Runs system ui tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

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
  dependsOn(provider { tasks.named("copyMainComposeFile") })
}

tasks.named<ShadowJar>("shadowJar") {
  dependsOn(provider { tasks.named("copyMainComposeFile") })
}

archUnit {
  setSkip(true)
}
