plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springCloud.bom))

  api(project(":common:common-properties"))
  api(project(":common:common-rpc"))
  api("org.springframework.cloud:spring-cloud-starter-vault-config")
}
