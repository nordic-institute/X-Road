plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.quarkus.bom)) //TODO consider dropping bom as it might impact other deps.

  implementation("io.smallrye.config:smallrye-config-core")
}
