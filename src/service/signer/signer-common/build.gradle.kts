plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":lib:properties-core"))
  implementation(project(":service:signer:signer-api"))
  implementation(platform(libs.jackson.bom))
  implementation("tools.jackson.core:jackson-databind")
  implementation(libs.quarkus.arc)
}
