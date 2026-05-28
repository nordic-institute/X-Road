plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.catalog)
  implementation(libs.edc.spi.contract)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.dsp)
  implementation(libs.edc.spi.dsp.v2025)
  implementation(libs.edc.spi.jsonld)
  implementation(libs.edc.spi.transfer)
  implementation(libs.edc.spi.web)
  implementation(libs.jakarta.annotationApi)

  implementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  implementation(project(":lib:rpc-core"))

  api(libs.edc.lib.controlplane.transform)

  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
  testImplementation(libs.junit.jupiter.params)
}
