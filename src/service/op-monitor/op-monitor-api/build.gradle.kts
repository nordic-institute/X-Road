plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:serverconf-core"))

  implementation(libs.smallrye.config.core)
  implementation(libs.jakarta.cdiApi)
}
