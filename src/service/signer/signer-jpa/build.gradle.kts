plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":lib:serverconf-impl"))
  implementation(libs.quarkus.arc)

  testImplementation(libs.mockito.core)
}
