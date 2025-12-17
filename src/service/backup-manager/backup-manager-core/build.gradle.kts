plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.scheduler)

  implementation(project(":service:backup-manager:backup-manager-rpc-client"))

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(testFixtures(project(":lib:properties-core")))
}
