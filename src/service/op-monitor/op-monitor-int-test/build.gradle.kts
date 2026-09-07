plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":tool:liquibase-executor"))
  intTestImplementation(project(":service:op-monitor:op-monitor-client"))
  intTestImplementation(project(":tool:api-test-core"))
  intTestImplementation(libs.liquibase.core)

  intTestRuntimeOnly(libs.postgresql)
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "DB_INIT_IMG" to "ss-db-init",
    "OP_MONITOR_IMG" to "ss-op-monitor"
  )
}

intTestShadowJar {
  archiveBaseName("opmonitor-int-test")
  mainClass("org.niis.xroad.opmonitor.test.ConsoleIntTestRunner")
}

tasks.register<Test>("intTest") {
  dependsOn(":service:op-monitor:op-monitor-application:quarkusBuild")
  dependsOn(provider { tasks.named("generateIntTestEnv") })

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}
