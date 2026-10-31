plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  implementation(project(":central-server:admin-service:core-api"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:properties-core"))

  implementation("org.springframework:spring-context")
  implementation("org.springframework:spring-tx")
  implementation(libs.jakarta.validationApi)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.springBoot.starterTest)

  testFixturesApi(project(":central-server:admin-service:core-api"))
  testFixturesApi(project(":lib:globalconf-core"))
  testFixturesImplementation(libs.springBoot.starterTest)
}
