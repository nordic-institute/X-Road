plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.jackson.bom))
  implementation("tools.jackson.core:jackson-databind")

  implementation(libs.slf4j.api)
  implementation(libs.smallrye.config.core)
  implementation(libs.hikariCP)

  testImplementation(libs.assertj.core)
  testImplementation(libs.h2database)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.systemStubs)

  testFixturesImplementation(libs.smallrye.config.core)
}
