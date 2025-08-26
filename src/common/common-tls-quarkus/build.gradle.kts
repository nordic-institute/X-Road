plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-tls"))
  api(libs.quarkus.arc)
  api(libs.quarkus.extension.vault)
}
