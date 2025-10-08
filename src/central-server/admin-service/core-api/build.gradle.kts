plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(platform(libs.springBoot.bom))
  api(project(":lib:globalconf-spring"))
  api(project(":service:signer:signer-client-spring"))
  api(project(":common:common-admin-api"))
  api(project(":common:common-management-request"))

  implementation(libs.jakarta.validationApi)

  testImplementation(project(":common:common-test"))
}
