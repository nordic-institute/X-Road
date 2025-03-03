plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-monitor"
    tags = setOf("latest")
  }
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))

  implementation(libs.quarkus.extension.systemd.notify)

  implementation(project(":service:monitor:monitor-core"))

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
