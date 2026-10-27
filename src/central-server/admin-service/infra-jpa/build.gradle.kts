plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":central-server:admin-service:core"))
  implementation(project(":common:common-domain"))

  api(libs.springBoot.starterDataJpa)
  api(libs.hibernate.core)
  implementation("org.hibernate.validator:hibernate-validator")

  testImplementation(libs.springBoot.starterTest)
  testImplementation(testFixtures(project(":central-server:admin-service:globalconf-generator")))
  testImplementation(project(":tool:liquibase-executor"))
  testImplementation(libs.liquibase.core)
  testImplementation(project(":common:common-db"))
  testImplementation(libs.postgresql)
  testImplementation(libs.hibernate.hikaricp)
  testImplementation(libs.awaitility)
  testImplementation(libs.testcontainers.postgresql)
}
