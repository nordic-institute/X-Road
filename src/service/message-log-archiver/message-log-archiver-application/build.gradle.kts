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
      put("quarkus.package.output-name", "messagelog-archiver-1.0")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }

        "containerized" -> {
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.group", "niis")
          put("quarkus.container-image.name", "xroad-messagelog-archiver")
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

  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.scheduler)

  testImplementation(libs.quarkus.junit5)
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
