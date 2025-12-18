plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":security-server:admin-service:message-log-archiver-api"))

  implementation(project(":lib:rpc-core"))
  implementation(project(":common:common-core"))
  implementation(libs.smallrye.config.core)
}

