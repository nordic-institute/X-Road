plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {

  implementation(project(":common:common-core"))
  implementation(project(":lib:health-check-core"))
  implementation(project(":common:common-jetty"))

  implementation(project(":lib:globalconf-impl"))

  implementation(project(":service:signer:signer-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":service:signer:signer-common"))
  implementation(project(":service:configuration-client:configuration-client-rpc-client"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:vault-quarkus"))
  implementation(project(":lib:properties-core"))

  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.scheduler)
  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.apache.commonsPool2)
  implementation(libs.resilience4j.retry)
  implementation(libs.resilience4j.timelimiter)
  implementation(libs.jakarta.validationApi)

  api(fileTree("../../../libs/pkcs11wrapper") { include("*.jar") })

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:properties-core")))
  testImplementation(project(":lib:properties-core"))
  testImplementation(libs.mockito.core)
}
