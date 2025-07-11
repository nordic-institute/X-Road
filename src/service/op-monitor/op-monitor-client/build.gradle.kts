plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":service:op-monitor:op-monitor-api"))
  api(project(":common:common-rpc"))
}
