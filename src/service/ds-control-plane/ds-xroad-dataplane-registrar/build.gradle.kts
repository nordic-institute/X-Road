plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-core"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.boot)
  implementation(libs.edc.spi.dataplane.selector)
  implementation(libs.edc.spi.participantcontext)
  implementation(libs.jakarta.annotationApi)
  implementation(libs.slf4j.api)

  testImplementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
}
