plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.transfer)

  implementation(project(":service:ds-control-plane:ds-xroad-dataplane-lifecycle-protocol"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.junit.jupiter.params)
}
