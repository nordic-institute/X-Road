plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  api(project(":lib:serverconf-impl"))

  implementation("org.springframework:spring-context-support")
}

