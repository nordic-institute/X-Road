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
  implementation(project(":common:common-domain"))
  implementation(project(":lib:globalconf-impl"))
  implementation(libs.nimbus.jose.jwt)
  // Owned replacement for org.eclipse.edc:jetty-core; this module compiles against its WebServer
  // implementation directly (XRoadIssuerRequestHeaderSizeExtension).
  implementation(project(":lib:edc-jetty-core"))
  implementation(libs.jetty.server)

  constraints {
    implementation(libs.jakarta.validationApi)
  }
}

archUnit {
  isSkip = true
}
