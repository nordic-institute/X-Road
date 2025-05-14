plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(project(":common:common-core"))
  implementation(project(":common:common-jetty"))

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":service:signer:signer-api"))

  implementation(libs.mapstruct)
  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.scheduler)
  implementation(libs.apache.commonsPool2)

  api(fileTree("../../../libs/pkcs11wrapper") { include("*.jar") })

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-properties")))
  testImplementation(libs.mockito.core)
}
