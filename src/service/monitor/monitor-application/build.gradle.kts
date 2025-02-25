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
  implementation(project(":lib:bootstrap-quarkus"))
  implementation(libs.bundles.quarkus.containerized)

  implementation(project(":service:monitor:monitor-core"))

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
