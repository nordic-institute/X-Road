plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.quarkus)
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":service:op-monitor:op-monitor-core"))

  testImplementation(libs.quarkus.junit5)
  testImplementation(testFixtures(project(":common:common-rpc")))
}

tasks.jar {
  enabled = false
}
