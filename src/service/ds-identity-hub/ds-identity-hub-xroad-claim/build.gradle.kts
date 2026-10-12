plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.identityhub)
  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.sts)
  implementation(libs.edc.ih.sts.core)
  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))
  implementation(libs.nimbus.jose.jwt)

  testImplementation(libs.assertj.core)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.boot)

  constraints {
    implementation(libs.jakarta.validationApi)
  }
}

archUnit {
  isSkip = true
}
