plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

configurations.named("intTestImplementation") {
  exclude(group = "org.jboss.logmanager", module = "jboss-logmanager")
}

dependencies {
  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(project(":common:common-jetty"))
//  implementation(project(":common:common-message"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.quarkus.scheduler)

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:keyconf-impl"))

  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation(libs.jetty.xml)
//  implementation(libs.xerces.impl)
  implementation(libs.semver4j)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
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
  testFixturesImplementation(testFixtures(project(":lib:keyconf-impl")))
  testFixturesImplementation(testFixtures(project(":lib:serverconf-impl")))
  testFixturesImplementation(libs.wsdl4j)

  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-int-test"))
}

tasks.register<Test>("intTest") {
  useJUnitPlatform()
  dependsOn(":service:signer:signer-application:quarkusBuild")

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()
  if (project.hasProperty("intTestProfilesInclude")) {
    intTestArgs += "-Dspring.profiles.include=${project.property("intTestProfilesInclude")}"
  }

  jvmArgs(intTestArgs)

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }

  reports {
    junitXml.required.set(false)
  }
}

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}

tasks.named("compileIntTestJava") {
  dependsOn(tasks.named("jandex"))
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  jvmArgs("-Xmx2G")
}

val runMetaserviceTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runMetaserviceTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
