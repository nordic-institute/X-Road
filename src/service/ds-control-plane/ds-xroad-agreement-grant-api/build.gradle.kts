plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.core)

  implementation(project(":service:ds-control-plane:ds-xroad-agreement-grant-protocol"))
  implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))
  implementation(project(":service:ds-control-plane:ds-xroad-catalog"))
  implementation(project(":common:common-domain"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.junit.jupiter.params)
}
