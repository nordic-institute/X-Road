plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:globalconf-impl"))
  implementation(libs.xerces.impl)

  testImplementation(project(":common:common-test"))
}
