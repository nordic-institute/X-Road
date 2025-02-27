plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-signer"
    tags = setOf("latest")
  }
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":common:common-core"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(libs.bundles.quarkus.core)

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
