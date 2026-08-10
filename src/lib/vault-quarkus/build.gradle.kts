plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:vault-core"))
  api(libs.quarkus.arc)
  api(libs.quarkus.extension.vault)

  testImplementation(libs.bouncyCastle.bcpkix)
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
}
