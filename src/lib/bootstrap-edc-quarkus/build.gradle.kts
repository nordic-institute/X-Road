plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.edc.boot)
  implementation(libs.bundles.quarkus.core)

  testImplementation(libs.junit.jupiter.params)
}
