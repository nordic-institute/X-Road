plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(libs.acme4j)
  api(libs.bouncyCastle.bcpkix)
  api(libs.slf4j.api)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
