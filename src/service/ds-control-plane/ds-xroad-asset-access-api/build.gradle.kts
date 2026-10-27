plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.dsp)
  implementation(libs.edc.spi.dsp.v2025)
  implementation(libs.apache.commonsLang3)
  implementation(libs.jakarta.annotationApi)

  implementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))
  implementation(project(":lib:ds-identity-core"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:edc-rpc"))

  api(libs.edc.lib.controlplane.transform)
  implementation(libs.edc.lib.dsp)
  implementation(libs.edc.lib.dsp.catalog.transform.v2025)

  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
  testImplementation(libs.junit.jupiter.params)

  testImplementation(project(":service:ds-control-plane:ds-xroad-contract-negotiation-store"))
  testImplementation(project(":lib:serverconf-core"))

  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.sql.contract.negotiation)
  testImplementation(libs.edc.sql.transfer.process)
  testImplementation(libs.edc.core.controlplane.transfer)
  testImplementation(libs.edc.core.dsp.http)
  testImplementation(libs.edc.spi.core)
  testImplementation(libs.edc.transaction.local)
  testImplementation(libs.edc.sql.lease)
  testImplementation(libs.edc.lib.core)
  testImplementation(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(testFixtures(libs.edc.sql.test.fixtures))
}
