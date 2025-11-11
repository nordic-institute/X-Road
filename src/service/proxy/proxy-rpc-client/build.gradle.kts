plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-rpc"))
  implementation(project(":common:common-core"))
  implementation(libs.smallrye.config.core)
}

