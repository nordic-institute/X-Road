plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(project(":service:signer:signer-client"))
  intTestImplementation(project(":common:common-core"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":lib:properties-core"))
}

intTestComposeEnv {
  images(
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "SIGNER_IMG" to "ss-signer",
    "CA_IMG" to "testca-dev"
  )
}

intTestShadowJar {
  archiveBaseName("signer-int-test")
  mainClass("org.niis.xroad.signer.test.ConsoleIntTestRunner")
}

tasks.register<Test>("intTest") {
  dependsOn(":service:signer:signer-application:quarkusBuild")
  dependsOn(provider { tasks.named("generateIntTestEnv") })

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

tasks.named<Copy>("processIntTestResources") {
  from("../../../../development/docker/testca-dev")
}

archUnit {
  setSkip(true)
}
