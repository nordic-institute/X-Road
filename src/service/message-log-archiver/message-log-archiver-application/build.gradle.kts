plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put(
        "quarkus.container-image.image",
        "${project.property("xroadImageRegistry")}/ss-message-log-archiver:${project.findProperty("xroadServiceImageTag")}"
      )
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))

  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  implementation(project(":service:message-log-archiver:message-log-archiver-core"))

  testImplementation(libs.quarkus.junit5)
}
