plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":common:common-rpc-quarkus"))

  implementation(libs.smallrye.config.core)
  implementation(libs.jakarta.cdiApi)
}
