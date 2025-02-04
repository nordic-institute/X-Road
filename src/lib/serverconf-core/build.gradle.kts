plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-domain"))

  implementation(libs.smallrye.config.core)
}
