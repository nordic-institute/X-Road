plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  // X-Road-owned replacement for the upstream org.eclipse.edc:jetty-core artifact - sources the
  // HTTPS keystore from OpenBao with hot reload instead of a file path. Every ds-* application
  // depends on this module transitively through bootstrap-edc-quarkus, so the upstream artifact
  // must never be added back here, to any ds-* application, or to any dependency they pull in
  // (org.eclipse.edc:http is a known transitive carrier of it - always exclude it there too).
  implementation(project(":lib:edc-jetty-core"))
  implementation(project(":lib:vault-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(libs.edc.spi.core)
  implementation(libs.jetty.server)
  implementation(libs.bundles.quarkus.core)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
  testImplementation(libs.bouncyCastle.bcpkix)
}
