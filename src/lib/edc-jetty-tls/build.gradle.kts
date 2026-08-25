plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":lib:vault-core"))
  implementation(project(":lib:edc-tls-reload"))

  implementation(libs.edc.boot)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.web)
  implementation(libs.jetty.server)
  implementation(libs.jetty.ee10.servlet)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
