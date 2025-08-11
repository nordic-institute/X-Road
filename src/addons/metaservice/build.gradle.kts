plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":common:common-jetty"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

  testImplementation(project(":common:common-test"))

  testImplementation(libs.wiremock.standalone)
  testImplementation(libs.wsdl4j)
  testImplementation(libs.xmlunit.matchers)
  testImplementation(libs.hsqldb)
  testImplementation(libs.jsonUnit.assertj)

  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))

  testRuntimeOnly(libs.junit.platform.launcher)
}

val runMetaserviceTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runMetaserviceTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
