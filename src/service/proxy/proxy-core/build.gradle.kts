plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  id("xroad.test-fixtures-conventions")
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
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

  testFixturesImplementation(project(":common:common-test"))
  testFixturesImplementation(project(":common:common-jetty"))
  testFixturesImplementation(project(":common:common-messagelog"))
  testFixturesImplementation(project(":common:common-scheduler"))
  testFixturesImplementation(project(":service:op-monitor:op-monitor-api"))
  testFixturesImplementation(testFixtures(project(":lib:keyconf-impl")))
  testFixturesImplementation(testFixtures(project(":lib:serverconf-impl")))
  testFixturesImplementation(libs.wsdl4j)

  "intTestRuntimeOnly"(project(":service:signer:signer-application"))
  "intTestImplementation"(project(":common:common-test"))
  "intTestImplementation"(project(":common:common-int-test"))
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

tasks.withType<Test> {
  jvmArgs("-Xmx2G")
}
