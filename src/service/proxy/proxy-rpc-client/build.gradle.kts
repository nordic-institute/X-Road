plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":lib:globalconf-core")) //for the ee.ria.xroad.common.DiagnosticsStatus
  implementation(project(":common:common-rpc"))
}

