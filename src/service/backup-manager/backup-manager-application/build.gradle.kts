plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-properties-db-source-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:backup-manager:backup-manager-core"))

  implementation(libs.postgresql)
  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  testImplementation(libs.quarkus.junit5)
  testImplementation(project(":common:common-test"))
}
