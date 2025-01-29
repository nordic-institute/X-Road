plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":common:common-properties"))

  implementation("org.springframework.boot:spring-boot-starter")
}
