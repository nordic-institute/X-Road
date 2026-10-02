plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":service:op-monitor:op-monitor-api"))
  api(project(":lib:properties-core"))
  api(project(":lib:rpc-core"))

  testImplementation(libs.assertj.core)
}
