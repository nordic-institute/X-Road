plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":common:common-db"))
  implementation(project(":common:common-db-identifiers"))
  implementation(libs.quarkus.arc)

  testImplementation(libs.mockito.core)
}
