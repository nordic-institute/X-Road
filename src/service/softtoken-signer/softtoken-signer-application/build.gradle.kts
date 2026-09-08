plugins {
  alias(libs.plugins.jandex)
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:health-check-core"))
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":service:signer:signer-common"))
  implementation(project(":lib:rpc-quarkus"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:properties-core"))

  implementation(libs.smallrye.config.core)
  implementation(libs.bundles.quarkus.containerized)

  testImplementation(libs.assertj.core)
}

tasks.jar {
  enabled = true
}
