plugins {
  id("xroad.java-conventions")
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":lib:asic-core"))
  implementation("org.springframework.boot:spring-boot-starter")
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-pgp")))
}
