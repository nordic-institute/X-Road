plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  implementation(libs.edc.issuerservice.issuance.spi)
  implementation(libs.edc.issuerservice.holder.spi)
  implementation(libs.edc.spi.identityhub.dcp)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.lib.core)
  implementation(libs.edc.crypto.verifiablecredentials.jwt)
  implementation(project(":common:common-domain"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:edc-jetty-tls"))
  implementation(libs.nimbus.jose.jwt)
  implementation(libs.jetty.server)

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.core)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.issuer.issuance)

  testFixturesImplementation(libs.nimbus.jose.jwt)
  testFixturesImplementation(libs.bouncyCastle.bcpkix)

  constraints {
    implementation(libs.jakarta.validationApi)
  }
}

archUnit {
  isSkip = true
}
