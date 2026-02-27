plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:vault-core"))
  api(project(":lib:rpc-core"))
  api(libs.quarkus.arc)
  api(libs.quarkus.extension.vault)
  api(libs.quarkus.health)
}
