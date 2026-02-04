plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":service:monitor:monitor-api"))

  implementation(libs.smallrye.config.core)
}
