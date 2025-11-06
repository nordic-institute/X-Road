plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-message"))
  api(project(":lib:globalconf-core"))
  api(project(":service:configuration-client:configuration-client-rpc-client"))

  implementation(libs.smallrye.config.core)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.julOverSlf4j)

  testFixturesImplementation(project(":common:common-test"))
}

