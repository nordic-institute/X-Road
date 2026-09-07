plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)

  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
