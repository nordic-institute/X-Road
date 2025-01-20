plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-message"))
  api(project(":lib:globalconf-model"))

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.julOverSlf4j)
}

