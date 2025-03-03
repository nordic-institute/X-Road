plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-jetty"))
  api(project(":common:common-scheduler"))

  implementation(project(":service:configuration-client:configuration-client-model"))

  implementation(libs.quarkus.arc)
  implementation(libs.commons.cli)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.wiremock.standalone)
}
