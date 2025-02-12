plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.quarkus)
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "native"

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      // Common properties
      put("quarkus.package.output-directory", "libs")
      put("quarkus.package.output-name", "proxy-1.0")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }

        "containerized" -> {
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.group", "niis")
          put("quarkus.container-image.name", "xroad-proxy")
          put("quarkus.container-image.tag", "latest")
        }

        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(project(":service:proxy:proxy-core"))

  testImplementation(libs.restAssured)
  testImplementation(libs.apache.httpasyncclient)
  testImplementation(project(":common:common-jetty"))
  testImplementation(project(":common:common-test"))

  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))

  testImplementation(libs.quarkus.junit5)
}

val runProxyTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxyTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
