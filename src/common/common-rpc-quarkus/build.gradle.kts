plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-rpc"))

  api(libs.quarkus.arc)
  api(libs.quarkus.scheduler)
  api(libs.quarkus.extension.vault)
}
