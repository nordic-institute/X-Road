plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))
  implementation(libs.resilience4j.retry)
  implementation(project(":lib:properties-core"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.logback.classic)
}
