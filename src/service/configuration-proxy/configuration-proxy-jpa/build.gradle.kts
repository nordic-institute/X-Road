plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-db"))

  implementation(libs.commons.codec)
  implementation(libs.quarkus.arc)
}
