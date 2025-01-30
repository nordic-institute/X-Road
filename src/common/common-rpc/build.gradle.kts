plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-properties"))
  implementation(libs.slf4j.api)

  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.util)
  api(libs.grpc.nettyShaded)
  api(libs.protobuf.javaUtil)
  api(libs.jakarta.annotationApi)
  api(libs.quarkus.arc)

  testFixturesImplementation(libs.quarkus.junit5)
}
