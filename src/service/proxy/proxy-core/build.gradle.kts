plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(project(":lib:vault-quarkus"))
  implementation(project(":common:common-jetty"))
  implementation(project(":lib:messagelog-core"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":service:monitor:monitor-rpc-client"))
  implementation(project(":security-server:admin-service:management-rpc-client"))

  implementation(libs.quarkus.scheduler)

  implementation(project(":lib:asic-core"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:keyconf-impl"))


  implementation(project(":service::proxy:proxy-monitoring-api"))
  implementation(project(":service:monitor:monitor-api"))

  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation(libs.jetty.xml)
  implementation(libs.semver4j)

  testImplementation(project(":common:common-test"))
  testImplementation(project(":security-server:admin-service:message-log-archiver")) {
    exclude(group = "org.springframework.boot")
  }

  testImplementation(testFixtures(project(":lib:properties-core")))
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
  testFixturesImplementation(project(":lib:messagelog-core"))
  testFixturesImplementation(project(":service:op-monitor:op-monitor-api"))
  testFixturesImplementation(project(":service:monitor:monitor-rpc-client"))
  testFixturesImplementation(testFixtures(project(":lib:properties-core")))
  testFixturesImplementation(testFixtures(project(":lib:keyconf-impl")))
  testFixturesImplementation(testFixtures(project(":lib:serverconf-impl")))
  testFixturesImplementation(libs.wsdl4j)
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  jvmArgs("-Xmx2G")
}
