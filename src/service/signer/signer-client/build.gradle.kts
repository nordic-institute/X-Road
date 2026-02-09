plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  implementation(libs.smallrye.config.core)
  api(project(":service:signer:signer-api"))
}
