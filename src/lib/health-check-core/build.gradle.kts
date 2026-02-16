plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(libs.jakarta.cdiApi)
  api(libs.quarkus.health)
  api(libs.grpc.stub)
  api(libs.slf4j.api)

  // Optional dependency for Hibernate-based database checks
  compileOnly(project(":common:common-db"))

  // Optional dependency for OpenBao/Vault health checks
  compileOnly(libs.quarkus.extension.vault)

  // Optional dependency for RPC properties (used by PKI readiness check)
  compileOnly(project(":lib:rpc-core"))

  compileOnly(libs.lombok)
  annotationProcessor(libs.lombok)
}