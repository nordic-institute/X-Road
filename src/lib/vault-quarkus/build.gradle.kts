plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:vault-core"))
  api(libs.quarkus.arc)
  api(libs.quarkus.extension.vault)
}
