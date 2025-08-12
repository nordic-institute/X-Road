plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-jetty"))

  implementation(project(":service:configuration-client:configuration-client-model"))

  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.quartz)
  implementation(libs.commons.cli)
  implementation(libs.quarkus.jdbc.postgresql)
  implementation(libs.commons.dbutils)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.wiremock.standalone)
}
