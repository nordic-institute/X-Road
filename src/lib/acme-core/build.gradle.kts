plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-domain"))

  implementation(libs.acme4j)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
