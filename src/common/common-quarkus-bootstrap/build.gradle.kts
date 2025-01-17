plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-properties"))
  api(project(":common:common-rpc"))

  api(libs.bundles.quarkus.core)
  api(libs.quarkus.scheduler)
  api(libs.quarkus.springBoot.di)
}
