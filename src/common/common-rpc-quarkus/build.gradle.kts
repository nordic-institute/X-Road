plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-rpc"))
  api(project(":common:common-properties"))

  api(libs.quarkus.arc)
  api(libs.quarkus.scheduler)
  api(libs.quarkus.extension.vault)
}
