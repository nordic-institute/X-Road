plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
}
