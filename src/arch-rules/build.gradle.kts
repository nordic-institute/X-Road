plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(libs.jakarta.annotationApi)
  implementation("org.springframework:spring-context")

  implementation(libs.archUnit.plugin.core)
}

archUnit {
  isSkip = true // do not self-test
}
