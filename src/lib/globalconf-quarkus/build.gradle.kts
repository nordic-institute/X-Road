plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":lib:globalconf-impl"))

  implementation(project(":service:configuration-client:configuration-client-rpc-client"))
  implementation(libs.smallrye.config.core)
  api(libs.bundles.quarkus.core)
}

archUnit {
  isSkip = true
}
