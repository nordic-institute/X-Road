plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.issuerservice.issuance.spi)
  implementation(libs.edc.issuerservice.holder.spi)
  implementation(libs.edc.spi.identityhub.dcp)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.token)
  implementation(libs.edc.spi.jwt)
  implementation(libs.edc.spi.keys)
  implementation(libs.edc.lib.token)
  implementation(libs.edc.crypto.verifiablecredentials.jwt)
  implementation(libs.smallrye.config.core)
  implementation(project(":common:common-domain"))
  implementation(project(":lib:globalconf-impl"))
  implementation(libs.nimbus.jose.jwt)
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}