plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(libs.jakarta.annotationApi)
  implementation("org.springframework:spring-context")

  implementation(libs.archUnit.plugin.core)
}

archUnit {
  setSkip(true) // do not self-test
}
