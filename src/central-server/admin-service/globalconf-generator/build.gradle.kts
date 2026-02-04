plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":central-server:admin-service:core-api"))
  implementation(project(":lib:globalconf-core"))

  implementation("org.springframework:spring-context")
  implementation("org.springframework:spring-tx")
  implementation(libs.jakarta.validationApi)

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
