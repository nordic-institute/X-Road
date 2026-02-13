plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

// asic-core brings it, todo
configurations.named("implementation") {
  exclude(module = "globalconf-impl")
  exclude(module = "configuration-client-rpc-client")
  exclude(module = "configuration-client-model")
  exclude(module = "rpc-core")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(libs.bundles.quarkus.core)

  implementation(project(":lib:properties-quarkus"))
  implementation(project(":service:message-log-archiver:message-log-archiver-core"))
}

tasks.jar {
  enabled = false
}
