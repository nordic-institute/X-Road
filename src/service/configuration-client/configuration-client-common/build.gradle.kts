plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-jetty"))

  implementation(libs.commons.dbutils)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.wiremock.standalone)

  testImplementation(project(":central-server:admin-service:core-api"))
  testImplementation(project(":central-server:admin-service:globalconf-generator"))

}


