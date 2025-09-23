plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
  id("xroad.jboss-test-logging-conventions")
}

dependencies {
  annotationProcessor(libs.hibernate.jpamodelgen)
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  api(project(":common:common-db"))
  implementation(project(":common:common-domain"))
  implementation(libs.jakarta.cdiApi)
  implementation(libs.mapstruct)

  testImplementation(libs.hsqldb)
  testImplementation(project(":common:common-test"))
}
