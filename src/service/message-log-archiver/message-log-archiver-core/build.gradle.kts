plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}


dependencies {
  annotationProcessor(libs.mapstructProcessor)

  implementation(project(":common:common-db"))
  implementation(project(":common:common-domain"))
  implementation(project(":lib:asic-core"))
  implementation(project(":lib:messagelog-core"))
  implementation(project(":lib:properties-core"))
  implementation(project(":lib:vault-quarkus"))

  implementation(libs.mapstruct)

  testImplementation(libs.quarkus.junit5)
  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-pgp")))
  testImplementation(testFixtures(project(":lib:properties-core")))

  testFixturesImplementation(project(":common:common-db"))
  testFixturesImplementation(project(":common:common-pgp"))
  testFixturesImplementation(project(":lib:messagelog-core"))
  testFixturesImplementation(project(":lib:vault-quarkus"))
}
