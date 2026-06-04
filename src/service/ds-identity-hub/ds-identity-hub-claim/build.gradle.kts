plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))
  implementation(libs.nimbus.jose.jwt)

  constraints {
    implementation(libs.jakarta.validationApi)
  }
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}
