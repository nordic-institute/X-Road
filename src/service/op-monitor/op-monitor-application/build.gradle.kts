plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.package.output-name", "op-monitor-daemon-1.0")
    }
  )
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-op-monitor"
    tags = setOf("latest")
  }
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":service:op-monitor:op-monitor-core"))

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
