plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-proxy"
    tags = setOf("latest")
  }
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {

      put("quarkus.package.output-name", "proxy-1.0")
      when (buildType) {
        "native" -> {
          put("quarkus.container-image.build", "false")
          put("quarkus.package.output-directory", "libs")
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }

        "containerized" -> {
          put("quarkus.package.jar.type", "fast-jar")
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.registry", "${project.property("xroadImageRegistry")}")
          put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-proxy")
          put("quarkus.container-image.insecure", "true")
          put("quarkus.container-image.push", "true")
          put("quarkus.container-image.builder", "jib")

          put("quarkus.jib.working-directory", "/opt/app")
          put("quarkus.jib.base-jvm-image", "${project.property("xroadImageRegistry")}/ss-baseline-runtime:latest")
          put("quarkus.jib.platforms", "linux/amd64,linux/arm64/v8")

          put("quarkus.jib.jvm-entrypoint", "/bin/sh,/opt/app/entrypoint.sh")
          put("quarkus.jib.user", "xroad")
        }

        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
    }
  )
}

configurations.configureEach {
  exclude(group = "xml-apis", module = "xml-apis") // This library interferes with Jetty
}

dependencies {
  implementation(enforcedPlatform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.containerImage.jib)
  testImplementation(libs.quarkus.junit5)
  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
}

val runProxyTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxyTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
