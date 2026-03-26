plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(libs.commons.cli)
  implementation(libs.cliche)

  implementation(libs.bundles.quarkus.core)

  implementation(project(":service:configuration-proxy:configuration-proxy-common"))
  implementation(project(":service:configuration-proxy:configuration-proxy-jpa"))

  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:rpc-quarkus"))

  implementation(project(":service:signer:signer-client"))

  testImplementation(libs.quarkus.junit5)
  testImplementation(libs.mockito.jupiter)
  testImplementation(project(":common:common-test"))
  testImplementation(libs.assertj.core)
}

tasks.jar {
  enabled = false
}
