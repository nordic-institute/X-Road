plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springCloud.bom))

  api(project(":lib:properties-core"))
  api(project(":lib:rpc-core"))
  api(project(":lib:vault-spring"))
  api("org.springframework.cloud:spring-cloud-starter-vault-config")
}
