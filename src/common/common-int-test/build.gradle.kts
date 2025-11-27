plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-message"))
  api(project(":lib:globalconf-core"))
  api(project(":service:signer:signer-client"))

  api(libs.bundles.testAutomation)

  api(libs.feign.hc5)
  api(libs.bouncyCastle.bcpkix)
  api(libs.awaitility)
}

tasks.test {
  useJUnitPlatform()
}

archUnit {
  setSkip(true)
}
