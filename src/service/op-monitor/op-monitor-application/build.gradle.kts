plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:rpc-quarkus"))
  implementation(project(":service:op-monitor:op-monitor-core"))

  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)
}

tasks.jar {
  enabled = false
}
