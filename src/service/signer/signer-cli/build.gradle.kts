plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(libs.commons.cli)
  implementation(libs.cliche)

  implementation(libs.bundles.quarkus.core)

  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":lib:bootstrap-quarkus"))

  testImplementation(libs.quarkus.junit5)
  testImplementation(libs.mockito.jupiter)
}

tasks.jar {
  enabled = false
}
