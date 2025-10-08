plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))

  implementation(libs.quarkus.scheduler)
}
