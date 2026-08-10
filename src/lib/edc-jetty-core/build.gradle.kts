plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(libs.edc.spi.core)
  api(libs.edc.spi.web)
  api(libs.jetty.server)

  implementation(project(":lib:vault-core"))
  implementation(libs.jetty.ee10.servlet)
  compileOnly(libs.jakarta.servletApi)

  testImplementation(libs.jakarta.servletApi)
  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
  testImplementation(libs.mockito.jupiter)
  testImplementation(project(":common:common-test"))
}
