plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-api-throttling"))
  api(project(":central-server:management-service:core-api"))

  api(project(":central-server:admin-service:api-client"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":common:common-domain"))
  implementation("org.springframework.boot:spring-boot-starter-web") {
    exclude(module = "spring-webmvc")
    exclude(module = "spring-boot-starter-json")
  }

  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
