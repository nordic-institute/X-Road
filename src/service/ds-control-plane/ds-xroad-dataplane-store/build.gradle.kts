plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.boot)
  implementation(libs.edc.spi.dataplane.selector)
  implementation(libs.jakarta.annotationApi)
  implementation(libs.slf4j.api)

  testImplementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  testImplementation(libs.edc.core.dataplane.selector)
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
