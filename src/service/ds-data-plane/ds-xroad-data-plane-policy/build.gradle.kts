plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.policy.engine)
  implementation(libs.edc.spi.contract)
  implementation(libs.edc.spi.dataplane)
  implementation(libs.edc.lib.http)
  implementation(libs.edc.spi.jsonld)
  implementation(libs.edc.lib.controlplane.transform)
  implementation(libs.edc.spi.participant)
  implementation(libs.edc.spi.transform)

  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":common:common-domain"))
  implementation(libs.guava)

  testImplementation(libs.assertj.core)
}
