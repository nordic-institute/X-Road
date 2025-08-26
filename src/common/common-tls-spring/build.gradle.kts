plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springCloud.bom))

  api(project(":common:common-tls"))
  api("org.springframework.cloud:spring-cloud-starter-vault-config")
}
