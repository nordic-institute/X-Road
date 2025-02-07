plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:globalconf-impl"))

  implementation("org.springframework.boot:spring-boot-starter")
}
