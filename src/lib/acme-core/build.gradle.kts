plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(libs.acme4j)
  api(libs.bouncyCastle.bcpkix)
  api(libs.slf4j.api)
  api(project(":common:common-domain"))
  api(project(":lib:globalconf-core"))

  implementation(libs.apache.commonsLang3)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.wiremock.standalone)
}
