plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-issuer-service:ds-issuer-service-provisioning-protocol"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:bootstrap-edc-quarkus"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identity.did)
  implementation(libs.edc.issuerservice.issuance.spi)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
