plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(platform(libs.jackson.bom))
  implementation(project(":lib:globalconf-core"))

  implementation(libs.quarkus.caffeine)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.lib.core)
  implementation(libs.okhttp)
  implementation("tools.jackson.core:jackson-databind")

  testImplementation(project(":common:common-test"))
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.awaitility)
  testImplementation(libs.nimbus.jose.jwt)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.ih.identity.did.core)
  testImplementation(libs.edc.ih.identity.did.web)
  testImplementation(libs.edc.crypto.verifiablecredentials)
}
