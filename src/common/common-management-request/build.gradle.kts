plugins {
  id("xroad.java-conventions")
  id("java-test-fixtures")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.springBoot.starterWeb)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.springBoot.starterTest)

  testFixturesApi(project(":common:common-message"))
  testFixturesApi(project(":common:common-test"))
  testFixturesApi(project(":service:signer:signer-client"))
  testFixturesApi(project(":lib:globalconf-core"))

  testFixturesCompileOnly(libs.lombok)
  testFixturesAnnotationProcessor(libs.lombok)
}
