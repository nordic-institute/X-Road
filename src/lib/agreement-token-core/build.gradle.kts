plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-domain"))
  api(project(":lib:vault-core"))
  api(project(":lib:serverconf-core"))
  api(libs.nimbus.jose.jwt)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}
