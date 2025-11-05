plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}


configurations.named("runtimeClasspath") {
  exclude(group = "xml-apis", module = "xml-apis")
}
configurations.named("testRuntimeClasspath") {
  exclude(group = "xml-apis", module = "xml-apis")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-properties-db-source-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.bundles.quarkus.containerized)

  implementation(libs.quarkus.extension.systemd.notify)

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
