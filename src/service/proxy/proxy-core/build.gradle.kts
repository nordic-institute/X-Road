plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  api(platform(libs.springBoot.bom))

  implementation(project(":common:common-jetty"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:signer:signer-client"))

  api(project(":lib:globalconf-spring"))
  api(project(":lib:serverconf-spring"))
  api(project(":lib:keyconf-impl"))

  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  api("org.springframework:spring-context-support")
  implementation(libs.jetty.xml)
  implementation(libs.xerces.impl)
  implementation(libs.semver4j)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(libs.wsdl4j)
  testImplementation(libs.restAssured)

  testRuntimeOnly(libs.junit.platform.launcher)

  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":common:common-jetty"))
  testFixturesImplementation(project(":common:common-messagelog"))
  testFixturesImplementation(project(":common:common-scheduler"))
  testFixturesImplementation(project(":service:op-monitor:op-monitor-api"))
  testFixturesImplementation(testFixtures(project(":lib:keyconf-impl")))
  testFixturesImplementation(testFixtures(project(":lib:serverconf-impl")))
  testFixturesImplementation(libs.wsdl4j)
}

tasks.withType<Test> {
  jvmArgs("-Xmx2G")
}
