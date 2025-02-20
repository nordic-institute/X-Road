plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "containerized"

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.package.output-name", "monitor-1.0")
    }
  )
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

  implementation(project(":service:monitor:monitor-core"))

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
