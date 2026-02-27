plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:globalconf-impl"))
  implementation(libs.xerces.impl)

  testImplementation(project(":common:common-test"))
}
