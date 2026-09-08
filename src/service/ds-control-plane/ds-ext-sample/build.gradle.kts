plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.boot)
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:rpc-quarkus"))
}
