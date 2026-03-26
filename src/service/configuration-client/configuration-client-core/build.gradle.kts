plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":lib:globalconf-core"))

  implementation(project(":service:configuration-client:configuration-client-model"))
  implementation(project(":service:configuration-client:configuration-client-common"))

  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.quartz)
  implementation(libs.quarkus.jdbc.postgresql)

  api(libs.commons.cli)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:properties-core")))
}
