plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(libs.bundles.metrics)
  implementation(libs.quarkus.scheduler)

  testImplementation(project(":common:common-test"))
}
