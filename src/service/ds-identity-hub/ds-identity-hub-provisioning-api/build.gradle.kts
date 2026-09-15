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
  implementation(libs.edc.lib.sql)
  implementation(libs.edc.spi.transaction.datasource)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.junit)
  testImplementation(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.edc.ih.sql.holder.credential.request)
  testImplementation(testFixtures(libs.edc.sql.test.fixtures))
  testImplementation(libs.awaitility)
}