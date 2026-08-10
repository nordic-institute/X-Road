plugins {
  id("xroad.java-conventions")
  id("java-test-fixtures")
}

dependencies {
  api(project(":common:common-domain"))

  implementation(libs.acme4j)

  testFixturesImplementation(libs.acme4j)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
