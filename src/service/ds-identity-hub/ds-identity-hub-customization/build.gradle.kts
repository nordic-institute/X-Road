plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.ih.participants)
  implementation(libs.edc.spi.participantcontext.config)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.web)
  implementation(libs.edc.spi.identity.did)
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.identityhub.did)
  implementation(libs.jakarta.validationApi)

  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:ds-identity-core"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}
