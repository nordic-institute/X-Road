plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  implementation(project(":common:common-core"))

  implementation(libs.bouncyCastle.bcpg)

  testFixturesImplementation(libs.bouncyCastle.bcpg)
  testFixturesImplementation(libs.slf4j.api)
}
