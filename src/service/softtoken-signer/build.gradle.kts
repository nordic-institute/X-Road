plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":common:common-signer"))

  implementation(libs.smallrye.config.core)
  implementation(libs.quarkus.arc)
}
