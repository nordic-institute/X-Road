plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-issuer-service:ds-issuer-service-provisioning-protocol"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))
  implementation(project(":lib:properties-core"))
  implementation(project(":lib:ds-identity-core"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.lib.core)
  implementation(libs.edc.ih.identity.did.web)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.issuerservice.issuance.spi)
  implementation(libs.edc.issuerservice.credential.spi)

  testImplementation(project(":lib:bootstrap-edc-quarkus"))
  testImplementation(project(":lib:globalconf-core"))
  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.edc.lib.jsonld)
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
