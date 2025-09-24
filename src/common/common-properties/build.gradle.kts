plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.slf4j.api)
  implementation(libs.microprofile.config.api)
  implementation(libs.hikariCP)

  testImplementation(libs.h2database)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.systemStubs)

  testFixturesImplementation(libs.smallrye.config.core)
}
