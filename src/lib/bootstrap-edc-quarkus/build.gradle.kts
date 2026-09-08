plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:edc-tls-reload"))
  implementation(project(":lib:edc-tls-trust"))

  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  implementation(libs.edc.spi.core)
  implementation(libs.bundles.quarkus.core)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
