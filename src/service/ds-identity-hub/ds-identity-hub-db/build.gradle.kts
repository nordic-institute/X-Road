plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.bom.identityhub.sql)
  implementation(libs.edc.bom.issuerservice.sql)
  implementation(libs.edc.store.participantcontext.config.sql)
}

archUnit {
  setSkip(true)
}
