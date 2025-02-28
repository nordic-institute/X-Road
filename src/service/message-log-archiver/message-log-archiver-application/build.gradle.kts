plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

jib {
  to {
    image = "${project.property("xroadImageRegistry")}/ss-message-log-archiver"
    tags = setOf("latest")
  }
}

dependencies {
  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))
  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.scheduler)

  testImplementation(libs.quarkus.junit5)
}
