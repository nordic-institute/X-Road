plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-message"))
  api(project(":lib:globalconf-core"))
  api(project(":service:configuration-client:configuration-client-rpc-client"))

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.julOverSlf4j)

  testFixturesImplementation(project(":common:common-test"))
}

