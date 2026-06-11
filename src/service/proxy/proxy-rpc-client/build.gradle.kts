plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":lib:properties-core"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":common:common-core"))
}

