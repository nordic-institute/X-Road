plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-identity-hub:ds-identity-hub-provisioning-protocol"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identityhub.vc)
  implementation(libs.edc.spi.identityhub.holdercredentialrequest)
  implementation(libs.edc.lib.core)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.spi.identityhub)
  testImplementation(libs.edc.spi.keypair)
  testImplementation(libs.edc.spi.controlplane)
  testImplementation(libs.edc.lib.core)
  testImplementation(libs.edc.ih.participants)
  testImplementation(libs.edc.ih.participant.context.core)
  testImplementation(libs.edc.ih.did)
  testImplementation(libs.edc.core.runtime)
  testImplementation(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.edc.ih.sql.holder.credential.request)
  testImplementation(testFixtures(libs.edc.sql.test.fixtures))
  testImplementation(libs.awaitility)

  constraints {
    testImplementation(libs.jakarta.validationApi)
  }
}
