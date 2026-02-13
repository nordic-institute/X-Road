plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(libs.bundles.quarkus.containerized)
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:rpc-quarkus"))

  implementation(libs.quarkus.extension.systemd.notify)

  implementation(project(":service:monitor:monitor-core"))
}

tasks.jar {
  enabled = false
}
