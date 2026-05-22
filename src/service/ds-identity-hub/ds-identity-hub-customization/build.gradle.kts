plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.ih.participants)
  implementation(libs.edc.spi.participantcontext.config)
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.identityhub.participantcontext)
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}
