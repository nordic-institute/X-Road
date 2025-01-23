plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-core"))

  api(libs.quartz) {
    exclude(module = "c3p0")
  }
}
