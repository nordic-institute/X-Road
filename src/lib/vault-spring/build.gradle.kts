plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springCloud.bom))

  api(project(":lib:properties-core"))
  api(project(":lib:vault-core"))
  api("org.springframework.cloud:spring-cloud-starter-vault-config")
  implementation(libs.microprofile.config.api)
}
