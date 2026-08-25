plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  implementation(libs.edc.spi.core)
  implementation(libs.bundles.quarkus.core)

  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
}
