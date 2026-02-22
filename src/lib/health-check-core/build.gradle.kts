plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(libs.jakarta.cdiApi)
  api(libs.quarkus.health)
  api(libs.grpc.stub)
  api(libs.slf4j.api)

  implementation(project(":common:common-db"))

  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)
}
