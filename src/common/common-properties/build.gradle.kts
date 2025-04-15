plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.caffeine)
  implementation(libs.slf4j.api)
  implementation(libs.smallrye.config.core)

  testImplementation(libs.h2database)
  testImplementation(libs.mockito.jupiter)

  testFixturesImplementation(libs.smallrye.config.core)
}
