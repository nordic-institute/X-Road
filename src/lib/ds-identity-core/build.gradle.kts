plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

dependencies {
  api(project(":common:common-domain"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
