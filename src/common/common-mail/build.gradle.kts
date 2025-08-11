plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-core"))

  implementation(platform(libs.springBoot.bom))

  implementation("org.springframework.boot:spring-boot-starter-mail")

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
