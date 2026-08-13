plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":lib:acme-core"))
  api(project(":lib:globalconf-spring"))

  implementation(libs.springBoot.starter)
}
