plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-control-plane:ds-xroad-provisioning-protocol"))
  implementation(project(":service:ds-control-plane:ds-xroad-dataplane-registrar"))
  implementation(project(":service:ds-control-plane:ds-xroad-catalog"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.participantcontext)
  implementation(libs.edc.spi.participantcontext.config)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
