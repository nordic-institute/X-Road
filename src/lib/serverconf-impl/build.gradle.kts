plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  implementation(project(":lib:vault-core"))
  api(project(":common:common-db"))
  api(project(":common:common-db-identifiers"))
  api(project(":lib:serverconf-core"))
  api(project(":lib:globalconf-impl"))

  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(libs.jakarta.validationApi)
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.hsqldb)
  testImplementation(libs.hibernate.hikaricp)
  testImplementation(testFixtures(project(":lib:properties-core")))

  testFixturesImplementation(project(":common:common-test"))
}
