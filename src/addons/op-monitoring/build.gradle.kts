plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:op-monitor:op-monitor-api"))

  implementation(project(":proxy:core"))
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-jetty"))
  implementation(project(":lib:serverconf-impl"))

  testImplementation(project(":common:common-test"))
  testImplementation(libs.commons.cli)
}
