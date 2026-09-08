plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:rpc-core"))
  api(project(":lib:vault-quarkus"))

  api(libs.quarkus.arc)
  api(libs.quarkus.scheduler)
  api(libs.quarkus.extension.vault)
}
