plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":lib:vault-core"))
  implementation(project(":lib:vault-quarkus"))
  implementation(project(":lib:properties-core"))
  implementation(libs.edc.spi.core)
  testImplementation(testFixtures(project(":lib:vault-core")))
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
