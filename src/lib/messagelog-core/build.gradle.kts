plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(project(":common:common-db"))
  implementation(project(":common:common-message"))
  implementation(project(":lib:asic-core"))
  implementation(project(":lib:properties-core"))

  implementation(libs.mapstruct)
  implementation(libs.smallrye.config.core)
  implementation(libs.jakarta.cdiApi)

  api(libs.bouncyCastle.bcpg)
  api(libs.bouncyCastle.bcpkix)
  api(project(":common:common-pgp"))
  api(project(":lib:vault-core"))

  testImplementation(project(":common:common-test"))
  testImplementation(project(":lib:properties-core"))
}
