plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(libs.edc.spi.controlplane)
  api(project(":common:common-core"))
  implementation(libs.edc.spi.core)

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.lib.core)
  testImplementation(libs.edc.core.participantcontext.config)
  testImplementation(libs.edc.junit)

  constraints {
    testImplementation(libs.jakarta.validationApi)
  }
}

archUnit {
  isSkip = true
}
