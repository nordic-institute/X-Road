plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-domain"))
  implementation(project(":common:common-rpc-spring"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":lib:serverconf-spring"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":service:signer:signer-client"))

  implementation("org.springframework:spring-context-support")

  implementation(libs.slf4j.api)
  implementation(libs.bundles.metrics)

  testImplementation(project(":common:common-test"))
}
