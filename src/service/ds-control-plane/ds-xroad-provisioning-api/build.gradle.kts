plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:ds-control-plane:ds-xroad-provisioning-protocol"))
  implementation(project(":service:ds-control-plane:ds-xroad-dataplane-registrar"))
  implementation(project(":service:ds-control-plane:ds-xroad-catalog"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.participantcontext)
  implementation(libs.edc.spi.participantcontext.config)
  implementation(libs.edc.spi.transaction)
  implementation(libs.edc.spi.transaction.datasource)
  implementation(libs.edc.lib.sql)
  implementation(libs.edc.store.participantcontext.config.sql)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
}
