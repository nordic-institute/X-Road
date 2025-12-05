plugins {
  id("xroad.java-config-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(libs.jakarta.annotationApi)
  implementation(libs.jakarta.cdiApi)
  implementation("org.springframework:spring-context")
  implementation(libs.archUnit.plugin.core)
}
