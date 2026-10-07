plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":lib:serverconf-core"))
  api(project(":lib:globalconf-core"))

  testImplementation(libs.assertj.core)
}
