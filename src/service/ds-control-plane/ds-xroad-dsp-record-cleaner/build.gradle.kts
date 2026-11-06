plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

dependencies {
  implementation(project(":lib:serverconf-impl"))

  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.sql.policy.monitor)
  implementation(libs.edc.lib.core)
  implementation(libs.edc.sql.core)
  implementation(libs.edc.sql.lease)

  testImplementation(project(":tool:liquibase-executor"))
  testImplementation(libs.liquibase.core)
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.lib.controlplane)
  testImplementation(libs.edc.core.controlplane.transfer)
  testImplementation(libs.edc.sql.contract.negotiation)
  testImplementation(libs.edc.sql.transfer.process)
  testImplementation(libs.edc.sql.core)
  testImplementation(libs.edc.sql.lease)
  testImplementation(libs.edc.transaction.local)
  testImplementation(libs.hibernate.hikaricp)
  testImplementation(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(testFixtures(libs.edc.spi.controlplane))
  testImplementation(testFixtures(libs.edc.sql.test.fixtures))
}
