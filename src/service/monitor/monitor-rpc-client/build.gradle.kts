plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-rpc"))
  api(project(":service:monitor:monitor-api"))
}
