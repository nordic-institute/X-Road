plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":service:signer:signer-client"))

  api(project(":lib:keyconf-api"))

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))

  testRuntimeOnly(libs.junit.platform.launcher)

  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":lib:serverconf-impl"))
}

