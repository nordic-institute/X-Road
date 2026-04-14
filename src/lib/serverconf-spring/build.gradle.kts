plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:serverconf-impl"))
  implementation(project(":lib:vault-spring"))

  implementation(libs.springBoot.starter)
}

