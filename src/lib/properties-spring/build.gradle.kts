plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:properties-core"))
  implementation(libs.springBoot.starter)
}
