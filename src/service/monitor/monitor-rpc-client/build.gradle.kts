plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":service:monitor:monitor-api"))
  api(project(":lib:properties-core"))
  api(project(":lib:rpc-core"))
}
