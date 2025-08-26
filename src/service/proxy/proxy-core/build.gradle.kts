plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(project(":common:common-tls-quarkus"))
  implementation(project(":common:common-jetty"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":service:monitor:monitor-rpc-client"))

  implementation(libs.quarkus.scheduler)

  implementation(project(":lib:asic-core"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:keyconf-impl"))


  implementation(project(":addons:proxymonitor-common"))
  implementation(project(":service:monitor:monitor-api"))

  implementation(project(":addons:messagelog:messagelog-db"))

  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation(libs.jetty.xml)
//  implementation(libs.xerces.impl)
  implementation(libs.semver4j)

  testImplementation(project(":common:common-test"))
  testImplementation(project(":service:message-log-archiver:message-log-archiver-core"))
  testImplementation(testFixtures(project(":common:common-properties")))
  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(libs.bouncyCastle.bcpg)
  testImplementation(libs.commons.cli)
  testImplementation(libs.hsqldb)
  testImplementation(libs.jsonUnit.assertj)
  testImplementation(libs.restAssured)
  testImplementation(libs.wiremock.standalone)
  testImplementation(libs.wsdl4j)
  testImplementation(libs.xmlunit.matchers)

  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":common:common-jetty"))
  testFixturesImplementation(project(":common:common-messagelog"))
  testFixturesImplementation(project(":service:op-monitor:op-monitor-api"))
  testFixturesImplementation(project(":service:monitor:monitor-rpc-client"))
  testFixturesImplementation(testFixtures(project(":common:common-properties")))
  testFixturesImplementation(testFixtures(project(":lib:keyconf-impl")))
  testFixturesImplementation(testFixtures(project(":lib:serverconf-impl")))
  testFixturesImplementation(libs.wsdl4j)
}

tasks.test {
  dependsOn("copyGpg")

  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  jvmArgs("-Xmx2G")
}

val runMetaserviceTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runMetaserviceTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}

tasks.register<JavaExec>("runProxymonitorMetaserviceTest") {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxymonitorMetaserviceTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}

tasks.register<Copy>("copyGpg") {
  description = "Copy GPG keys to build directory"
  group = "build"

  from("src/test/gpg")
  into(layout.buildDirectory.dir("gpg"))
}
