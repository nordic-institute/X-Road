plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":common:common-rpc-spring"))
  api(project(":service:signer:signer-client"))

  implementation("org.springframework.boot:spring-boot-starter")
}
