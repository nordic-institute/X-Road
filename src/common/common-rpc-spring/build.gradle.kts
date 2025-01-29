plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springCloud.bom))

  api(project(":common:common-rpc"))
  api(project(":common:common-properties-spring"))

  api("org.springframework.cloud:spring-cloud-starter-vault-config")
}
