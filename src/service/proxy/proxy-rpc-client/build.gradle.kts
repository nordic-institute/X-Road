plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  implementation(project(":service:configuration-client:configuration-client-model")) //TODO this is due to diagnostic status
  implementation(project(":common:common-rpc"))
  implementation(project(":common:common-core"))
}

