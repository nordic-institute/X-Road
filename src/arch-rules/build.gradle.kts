plugins {
  id("xroad.java-config-conventions")
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(libs.archUnit.plugin.core)
}
