plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.asset)
  implementation(libs.edc.spi.policy)
  implementation(libs.edc.spi.contract)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.boot)
  implementation(libs.edc.spi.policy.engine)
  implementation(libs.edc.spi.dataplane.http)

  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-domain"))
  implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.logback.classic)
}
