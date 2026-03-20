plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:rpc-core"))

  implementation(libs.smallrye.config.core)
}


