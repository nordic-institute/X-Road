plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":service:op-monitor:op-monitor-api"))
  api(project(":lib:rpc-core"))

  implementation(libs.smallrye.config.core)
}
