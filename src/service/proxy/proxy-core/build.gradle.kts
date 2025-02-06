plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  id("xroad.test-fixtures-conventions")
//  alias(libs.plugins.jandex)
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

configurations.configureEach {
  exclude(module = "logback-classic") //TODO remove once actual source is removed
  exclude(module = "logback-core") //TODO remove once actual source is removed
}

dependencies {
  api(platform(libs.quarkus.bom))

  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(project(":common:common-jetty"))
//  implementation(project(":common:common-message"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.quarkus.scheduler)

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:keyconf-impl"))

//  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation(libs.jetty.xml)
//  implementation(libs.xerces.impl)
  implementation(libs.semver4j)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:globalconf-impl")))
//  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
//  testImplementation(libs.wsdl4j)
//
  testFixturesImplementation(project(":common:common-domain"))
  testFixturesImplementation(project(":common:common-jetty"))
  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":common:common-message"))
  testFixturesImplementation(project(":lib:keyconf-api"))
  testFixturesImplementation(project(":lib:serverconf-impl"))
  testFixturesImplementation(libs.wsdl4j)
//
//  "intTestRuntimeOnly"(project(":service:signer:signer-application"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-int-test"))
}

val testJar by tasks.registering(Jar::class) {
  archiveBaseName.set("proxy-core")
  archiveClassifier.set("test")
  from(sourceSets.test.get().output)
}

tasks.register<Test>("intTest") {
  useJUnitPlatform()
  dependsOn(":service:signer:signer-application:shadowJar")

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
