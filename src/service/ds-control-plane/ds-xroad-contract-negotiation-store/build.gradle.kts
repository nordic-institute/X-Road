plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.sql.contract.negotiation)
  implementation(libs.edc.spi.transaction.datasource)
  implementation(libs.edc.lib.sql)
  implementation(libs.edc.sql.lease)
  implementation(libs.edc.sql.lease.spi)
  implementation(libs.edc.core.sql.bootstrapper)

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.edc.junit)
}
