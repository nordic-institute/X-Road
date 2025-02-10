plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.quarkus)
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      // Common properties
      put("quarkus.package.output-directory", "libs")
      put("quarkus.package.output-name", "signer-console-1.0")
      put("quarkus.package.jar.type", "uber-jar")
      put("quarkus.package.jar.add-runner-suffix", "false")
    }
  )
}

tasks {
  named<JavaCompile>("compileJava") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
  }
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(libs.commons.cli)
  implementation(libs.cliche)

  implementation(libs.bundles.quarkus.core)

  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))

  testImplementation(libs.quarkus.junit5)
  testImplementation(libs.mockito.jupiter)
}

tasks.jar {
  enabled = false
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
