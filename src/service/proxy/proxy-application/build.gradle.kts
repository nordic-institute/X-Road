plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.package.output-name", "proxy-1.0")
    }
  )
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-proxy"
    tags = setOf("latest")
  }
}

dependencies {
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(project(":service:proxy:proxy-core"))

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
