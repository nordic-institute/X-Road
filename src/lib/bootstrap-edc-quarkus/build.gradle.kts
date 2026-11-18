plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:edc-tls-reload"))
  implementation(project(":lib:edc-tls-trust"))
  implementation(project(":lib:properties-core"))

  implementation(libs.edc.boot)
  implementation(libs.edc.lib.core)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.core.sql.bootstrapper)
  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.health)

  testFixturesImplementation(libs.edc.boot)
  testFixturesApi(libs.edc.spi.core)
  testFixturesImplementation(libs.smallrye.config.yaml)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
