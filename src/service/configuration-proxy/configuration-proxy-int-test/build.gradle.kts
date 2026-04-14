import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(project(":service:configuration-proxy:configuration-proxy-core")) {
    exclude(group = "org.jboss.slf4j", module = "slf4j-jboss-logmanager")
  }
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "POSTGRES_DEV_IMG" to "postgres-dev",
    "DB_INIT_IMG" to "ss-db-init",
    "SIGNER_IMG" to "ss-signer",
    "NGINX_CP_IMG" to "nginx-cp",
    "CONFIGURATION_PROXY_IMG" to "configuration-proxy"
  )
}

intTestShadowJar {
  archiveBaseName("confproxy-int-test")
  mainClass("org.niis.xroad.confproxy.test.ConsoleIntTestRunner")
}

val copyMainComposeFile by tasks.registering(Copy::class) {
  description = "Copies main compose.yaml and nginx config to build directory"
  group = "verification"

  from("../../../../development/docker/configuration-proxy/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  from("../../../../development/docker/configuration-proxy/nginx-confproxy.conf")
  into("build/resources/intTest")
}

tasks.register<Test>("intTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(copyMainComposeFile)

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
  dependsOn(provider { tasks.named("copyMainComposeFile") })
}

tasks.named<ShadowJar>("shadowJar") {
  dependsOn(provider { tasks.named("copyMainComposeFile") })
}

archUnit {
  setSkip(true)
}
