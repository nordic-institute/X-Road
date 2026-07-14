plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-identity-hub:ds-identity-hub-provisioning-protocol"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identity.did)
  implementation(libs.edc.spi.identityhub.vc)
  implementation(libs.edc.spi.identityhub.holdercredentialrequest)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}