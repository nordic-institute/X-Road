plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.boot)

  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:ds-identity-core"))
  implementation(project(":common:common-domain"))
  implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.logback.classic)
}
