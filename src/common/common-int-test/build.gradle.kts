plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(project(":common:common-message"))
  api(project(":lib:globalconf-core"))
  api(project(":service:signer:signer-client"))

  api(libs.bundles.testAutomation)
  api(libs.test.selenide.core)
  api(libs.test.selenide.proxy)
  api(libs.test.allure.selenide)
  api(libs.feign.hc5)
  api(libs.bouncyCastle.bcpkix)
  api(libs.awaitility)
}

tasks.test {
  useJUnitPlatform()
}
