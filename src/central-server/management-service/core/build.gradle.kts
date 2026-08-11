plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-api-throttling"))
  api(project(":central-server:management-service:core-api"))

  api(project(":central-server:admin-service:api-client"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":lib:properties-core"))
  implementation(project(":common:common-domain"))
  implementation(libs.springBoot.starterWeb) {
    exclude(module = "spring-webmvc")
    exclude(module = "spring-boot-starter-json")
  }
  implementation(project(":common:common-management-service")) {
    exclude(module = "spring-boot-starter-tomcat")
  }

  testImplementation(libs.springBoot.starterTest)
}
