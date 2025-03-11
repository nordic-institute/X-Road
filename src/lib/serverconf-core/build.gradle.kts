plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-domain"))
  api(project(":common:common-db"))

  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(libs.jakarta.validationApi)
  implementation(libs.mapstruct)
}
