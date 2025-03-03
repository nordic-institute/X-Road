plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-signer")
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":common:common-core"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.bundles.quarkus.containerized)

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
