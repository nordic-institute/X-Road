import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:api-test-core"))
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

intTestPhasedSuite {
  phasedSuiteClass = "ConfProxyIntTestSuite"
  productName = "Configuration Proxy"
}

val copyMainComposeFile by tasks.registering(Copy::class) {
  description = "Copies main compose.yaml to build directory"
  group = "verification"

  from("../../../../development/docker/configuration-proxy/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  into("build/resources/intTest")
}

tasks.named<ShadowJar>("shadowJar") {
  dependsOn(provider { tasks.named("copyMainComposeFile") })
}
