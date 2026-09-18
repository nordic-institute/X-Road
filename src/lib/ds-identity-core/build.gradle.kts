plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

dependencies {
  api(project(":common:common-domain"))
  api(libs.carbon.did)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
