plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-rpc"))
  api(project(":service:monitor:monitor-api"))
}
