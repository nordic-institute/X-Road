plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}


configurations.named("runtimeClasspath") {
  exclude(group = "xml-apis", module = "xml-apis")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-properties-db-source-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.bundles.quarkus.containerized)

  implementation(libs.quarkus.extension.systemd.notify)
}
