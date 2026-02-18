plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.scheduler)

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":service:auxiliary-service:auxiliary-service-rpc-client"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(testFixtures(project(":lib:properties-core")))
}
