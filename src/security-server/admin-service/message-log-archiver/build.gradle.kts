plugins {
  id("xroad.java-conventions")
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-db"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":security-server:admin-service:message-log-archiver-api"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:asic-core"))

  api(project(":lib:messagelog-core"))

  implementation("org.springframework.boot:spring-boot-starter")
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-pgp")))
}
