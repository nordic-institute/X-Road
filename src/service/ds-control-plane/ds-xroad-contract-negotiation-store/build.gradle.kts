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
  testImplementation(libs.edc.spi.policy)
  testImplementation(libs.edc.lib.json)
  testImplementation(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(testFixtures(libs.edc.spi.contract))
  testImplementation(testFixtures(libs.edc.sql.test.fixtures))
}

// Feeds the canary test the version catalog's live "edc" pin as a generated test resource, so the recorded
// expectation in the test is compared against catalog reality instead of a second hardcoded copy of the pin.
val generateEdcVersionFixture = tasks.register("generateEdcVersionFixture") {
  val edcVersion = libs.versions.edc.get()
  val outputDir = layout.buildDirectory.dir("generated/resources/edcVersionFixture")
  outputs.dir(outputDir)
  doLast {
    outputDir.get().file("edc-version.txt").asFile.apply {
      parentFile.mkdirs()
      writeText(edcVersion)
    }
  }
}

sourceSets {
  test {
    resources.srcDir(generateEdcVersionFixture)
  }
}
