plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  annotationProcessor(libs.hibernate.jpamodelgen)
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  api(project(":common:common-db"))
  implementation(project(":common:common-domain"))
  implementation(libs.quarkus.arc)
  implementation(libs.mapstruct)

  testImplementation(libs.logback.classic)
  testImplementation(libs.hsqldb)
  testImplementation(project(":common:common-test"))
}
