plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.ih.participants)
  implementation(libs.edc.spi.participantcontext.config)
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.smallrye.config.core)
  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))
  implementation(libs.nimbus.jose.jwt)
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}
