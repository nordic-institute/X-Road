plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(libs.bundles.quarkus.core)

  implementation(project(":lib:properties-quarkus"))

  implementation(project(":service:message-log-archiver:message-log-archiver-core"))
  implementation(project(":lib:rpc-quarkus"))

//  implementation(project(":common:common-db"))
//  implementation(project(":common:common-pgp"))
//
//  testImplementation(libs.quarkus.junit5)
//  testImplementation(libs.mockito.jupiter)
}

tasks.jar {
  enabled = false
}
