plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))
  api(project(":lib:rpc-core"))
  api(project(":service:configuration-client:configuration-client-model"))

  implementation(libs.smallrye.config.core)
}
