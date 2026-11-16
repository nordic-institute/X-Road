plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-core"))
  implementation(project(":common:common-core"))
  implementation(project(":common:common-db"))
  implementation(project(":common:common-db-identifiers"))
  implementation(libs.quarkus.arc)

  testImplementation(libs.mockito.core)
  testImplementation(project(":common:common-test"))
  testImplementation(project(":tool:liquibase-executor"))
  testImplementation(libs.liquibase.core)
  testImplementation(libs.testcontainers.postgresql)
}
