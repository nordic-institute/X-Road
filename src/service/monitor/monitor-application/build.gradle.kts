plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.quarkus)
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "native"

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      // Common properties
      put("quarkus.package.output-directory", "libs")
      put("quarkus.package.output-name", "monitor-1.0")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }

        "containerized" -> {
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.group", "niis")
          put("quarkus.container-image.name", "xroad-monitor")
          put("quarkus.container-image.tag", "latest")
        }

        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
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
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(project(":service:monitor:monitor-core"))

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
