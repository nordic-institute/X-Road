plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))
  implementation(project(":common:common-properties"))
  implementation(libs.postgresql)
  implementation("org.springframework.boot:spring-boot-starter")
}
