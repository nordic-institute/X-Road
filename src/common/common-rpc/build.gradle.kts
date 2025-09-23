plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  api(project(":common:common-domain"))
  api(project(":common:common-properties"))
  api(project(":common:common-vault"))

  api(libs.slf4j.api)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.util)
  api(libs.grpc.nettyShaded)
  api(libs.protobuf.javaUtil)
  api(libs.jakarta.annotationApi)

  api(libs.jakarta.cdiApi)
  api(libs.microprofile.config.api)
  api(libs.resilience4j.retry)

  testFixturesImplementation(libs.quarkus.junit5)
  testImplementation(testFixtures(project(":common:common-properties")))
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.assertj.core)
}
