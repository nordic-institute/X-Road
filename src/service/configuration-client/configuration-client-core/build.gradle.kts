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
  implementation(libs.quarkus.jdbc.postgresql)
  implementation(libs.commons.dbutils)

  api(libs.commons.cli)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:properties-core")))
  testImplementation(libs.wiremock.standalone)
}
