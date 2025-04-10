plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.smallrye.config.core)

  testFixturesImplementation(libs.smallrye.config.core)
  testFixturesImplementation(libs.quarkus.core)
}
