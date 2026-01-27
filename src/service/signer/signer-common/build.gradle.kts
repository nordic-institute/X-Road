plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:signer:signer-api"))
  implementation(libs.quarkus.arc)
}
