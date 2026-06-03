plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.edc.lib.http)
  implementation(libs.bundles.quarkus.core)
  compileOnly(libs.edc.spi.web)
  compileOnly(libs.edc.core.jetty)
  compileOnly(libs.jetty.server)

  testImplementation(libs.junit.jupiter.params)
}
