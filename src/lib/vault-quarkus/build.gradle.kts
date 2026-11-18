plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:vault-core"))
  api(libs.quarkus.arc)
  api(libs.quarkus.extension.vault)
  implementation(libs.quarkus.scheduler)
  implementation(project(":lib:properties-core"))

  testImplementation(testFixtures(project(":lib:vault-core")))
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
