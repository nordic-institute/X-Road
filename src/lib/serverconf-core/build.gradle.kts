plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  api(project(":common:common-domain"))
  implementation("io.smallrye.config:smallrye-config-core")
}
