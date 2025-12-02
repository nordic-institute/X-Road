plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.shadow)
  alias(libs.plugins.allure)
}

dependencies {
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(libs.test.restassured)
  intTestImplementation(libs.postgresql)
  intTestImplementation(project(":lib:asic-core"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":lib:globalconf-impl"))
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "CS_IMG" to "central-server-dev",
    "POSTGRES_DEV_IMG" to "postgres-dev",
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
    "OP_MONITOR_IMG" to "ss-op-monitor",
    "CA_IMG" to "testca-dev"
  )
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

allure {
  adapter {
    frameworks {
      cucumber7Jvm
    }
  }
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(provider { tasks.named("copyComposeFiles") })
}

tasks.shadowJar {
  archiveBaseName.set("e2e-test")
  archiveClassifier.set("")
  isZip64 = true

  from(sourceSets["intTest"].output.classesDirs)

  from("${layout.buildDirectory.get().asFile}/resources/intTest") {
    into("")
  }
  from("${layout.buildDirectory.get().asFile}/resources/intTest/.env") {
    into("")
  }
  from(sourceSets["intTest"].runtimeClasspath.filter { it.name.endsWith(".jar") })

  mergeServiceFiles()
  exclude("**/module-info.class")

  manifest {
    attributes(
      "Main-Class" to "org.niis.xroad.e2e.ConsoleE2ETestRunner"
    )
  }

  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(provider { tasks.named("copyComposeFiles") })
  dependsOn(tasks.named("intTestClasses"))
  dependsOn(tasks.named("processIntTestResources"))
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

archUnit {
  setSkip(true)
}
