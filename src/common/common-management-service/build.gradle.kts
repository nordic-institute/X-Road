plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.springBoot.starterWeb)

  api(project(":common:common-vault-spring"))
}
