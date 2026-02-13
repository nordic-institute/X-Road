plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":service:signer:signer-client"))

  api(project(":lib:keyconf-api"))

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))

  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":lib:serverconf-impl"))
}

