plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:asic-core"))

  implementation(libs.smallrye.config.core)
  implementation(libs.jakarta.cdiApi)
  api(libs.bouncyCastle.bcpg)

  api(project(":common:common-pgp"))
  api(project(":common:common-vault"))

  testImplementation(project(":common:common-test"))
  testImplementation(libs.bouncyCastle.bcpg)
}
