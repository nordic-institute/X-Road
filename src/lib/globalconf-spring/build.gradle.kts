plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:globalconf-impl"))

  implementation(project(":lib:properties-core"))
  implementation(project(":service:configuration-client:configuration-client-rpc-client"))
  implementation(libs.springBoot.starter)
}
