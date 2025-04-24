plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":common:common-db"))
  api(project(":lib:serverconf-core"))
  api(project(":lib:globalconf-impl"))

  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(libs.jakarta.validationApi)
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.hsqldb)
  testImplementation(libs.hibernate.hikaricp)
  testImplementation(testFixtures(project(":common:common-properties")))

  testFixturesImplementation(project(":common:common-test"))
}
