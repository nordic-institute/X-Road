plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:properties-core"))
  implementation(project(":service:configuration-client:configuration-client-common"))

  implementation(project(":common:common-core"))
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-jetty"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.quarkus.arc)

  testImplementation(libs.quarkus.junit5)
  testImplementation(libs.mockito.jupiter)
  testImplementation(project(":common:common-test"))
  testImplementation(libs.assertj.core)
}
