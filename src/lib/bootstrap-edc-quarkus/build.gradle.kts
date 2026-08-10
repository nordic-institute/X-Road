plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  // X-Road-owned replacement for the upstream org.eclipse.edc:jetty-core artifact - sources the
  // HTTPS keystore from OpenBao with hot reload instead of a file path (see 05-owned-jetty-serving).
  // Every ds-* application depends on this module transitively through bootstrap-edc-quarkus, so the
  // upstream artifact must never be added back here or to any of those applications.
  implementation(project(":lib:edc-jetty-core"))
  implementation(libs.edc.spi.core)
  implementation(libs.jetty.server)
  implementation(libs.bundles.quarkus.core)

  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
}
