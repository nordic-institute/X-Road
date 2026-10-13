plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.boot)
  implementation(libs.jakarta.annotationApi)
  implementation(libs.slf4j.api)

  testImplementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  testImplementation(libs.edc.core.controlplane)
  testImplementation(libs.edc.core.controlplane.services)
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
