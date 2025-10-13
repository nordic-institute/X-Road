plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-spring"))

  implementation("org.springframework.boot:spring-boot-starter")

}
