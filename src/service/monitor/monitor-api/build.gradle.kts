plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  api(project(":common:common-rpc"))
  implementation(libs.guava)
  implementation(libs.slf4j.api)
}
