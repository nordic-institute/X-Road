plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.issuerservice.issuance.spi)
  implementation(libs.edc.issuerservice.holder.spi)
  implementation(libs.edc.spi.identityhub.dcp)
  implementation(libs.edc.spi.core)
}

tasks.withType<Checkstyle>().configureEach {
  isEnabled = false
}

archUnit {
  isSkip = true
}