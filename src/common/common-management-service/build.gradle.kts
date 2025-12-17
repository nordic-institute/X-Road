plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.springBoot.starterWeb)

  api(project(":lib:vault-spring"))
}
