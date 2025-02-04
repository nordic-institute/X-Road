plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-rpc"))
  api(project(":service:configuration-client:configuration-client-model"))

  implementation(libs.quarkus.arc)
}
