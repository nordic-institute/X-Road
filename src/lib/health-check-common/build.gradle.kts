plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(libs.microprofile.health.api)

  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)

  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.quarkus.health)
}
