plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}


dependencies {
  annotationProcessor(libs.mapstructProcessor)
  implementation(project(":common:common-db"))

  implementation(project(":lib:asic-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:messagelog-core"))

  implementation(libs.mapstruct)

//  implementation(platform(libs.quarkus.bom))
//  implementation(libs.bundles.quarkus.core)
//
//  implementation(project(":lib:vault-quarkus"))
//  implementation(project(":lib:properties-quarkus"))
//  implementation(project(":common:common-pgp"))
//
  testImplementation(libs.quarkus.junit5)
  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-pgp")))
//  testImplementation(libs.mockito.jupiter)
}

//tasks.jar {
//  enabled = false
//}
