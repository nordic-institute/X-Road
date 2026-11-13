plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.core)
  implementation(libs.jakarta.annotationApi)

  implementation(project(":lib:ds-identity-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":common:common-domain"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.core.connector)
  testImplementation(libs.edc.junit)
}
