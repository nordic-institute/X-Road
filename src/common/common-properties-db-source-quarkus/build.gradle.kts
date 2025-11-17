plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-properties"))
  implementation(libs.smallrye.config.core)
  implementation(libs.slf4j.api)
}
