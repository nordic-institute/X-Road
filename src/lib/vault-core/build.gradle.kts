plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))
  implementation(libs.resilience4j.retry)

  testImplementation(libs.assertj.core)
  testImplementation(libs.logback.classic)
}
