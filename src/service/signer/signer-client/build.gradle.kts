plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":service:signer:signer-api"))
}
