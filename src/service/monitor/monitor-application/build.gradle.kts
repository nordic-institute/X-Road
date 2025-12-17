plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:bootstrap-quarkus"))
  implementation(libs.bundles.quarkus.containerized)
  implementation(project(":common:common-properties-db-source-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))

  implementation(libs.quarkus.extension.systemd.notify)

  implementation(project(":service:monitor:monitor-core"))
}

tasks.jar {
  enabled = false
}
