plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:rpc-spring"))
  api(project(":service:signer:signer-client"))

  implementation(libs.springBoot.starter)
}
