plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-signer")
      put(
        "quarkus.jib.base-jvm-image",
        "${project.property("xroadImageRegistry")}/base-images/ss-baseline-signer-runtime:${project.findProperty("xroadBaseImageTag")}"
      )
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":common:common-core"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":service:signer:signer-jpa"))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  testImplementation(project(":common:common-db"))
  testImplementation(libs.quarkus.junit5)
  testImplementation(libs.hsqldb)
}

tasks.jar {
  enabled = false
}
