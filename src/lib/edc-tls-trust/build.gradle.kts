plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:edc-tls-reload"))

  implementation(libs.edc.spi.core)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
