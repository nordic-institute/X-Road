plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":lib:acme-core"))
  api(project(":lib:globalconf-spring"))

  implementation(project(":lib:properties-core"))
  implementation(project(":lib:properties-spring"))

  implementation(libs.springBoot.starter)
  implementation(libs.springBoot.starterWeb)

  testImplementation(libs.systemStubs)
}
