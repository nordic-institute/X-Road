plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  api(project(":lib:rpc-core"))

  implementation(project(":common:common-core"))
}
