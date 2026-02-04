plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(libs.smallrye.config.core)
  implementation(libs.slf4j.api)

  api(project(":lib:properties-core"))
  api(libs.bundles.quarkus.core)
}
