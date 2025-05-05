plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.jandex)
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(project(":common:common-core"))
  implementation(project(":common:common-jetty"))

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":service:signer:signer-api"))

  implementation(libs.mapstruct)
  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.scheduler)
  implementation(libs.apache.commonsPool2)

  api(fileTree("../../../libs/pkcs11wrapper") { include("*.jar") })

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-properties")))
  testImplementation(libs.mockito.core)

  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.logback.classic)

}

tasks.register<Test>("intTest") {
  useJUnitPlatform()

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

tasks.named("compileIntTestJava") {
  dependsOn(tasks.named("jandex"))
}

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}
