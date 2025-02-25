plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-proxy")
      put("quarkus.jib.jvm-entrypoint", "/bin/sh,/opt/app/entrypoint.sh")
    }
  )
}

configurations.configureEach {
  exclude(group = "xml-apis", module = "xml-apis") // This library interferes with Jetty
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.bundles.quarkus.containerized)

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
