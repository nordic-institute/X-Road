plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  implementation(libs.edc.core.jetty)
  implementation(libs.jetty.server)
  implementation(libs.bundles.quarkus.core)

  testImplementation(libs.junit.jupiter.params)
}
