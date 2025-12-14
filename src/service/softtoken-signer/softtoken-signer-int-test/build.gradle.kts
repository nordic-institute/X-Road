plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(project(":service:signer:signer-client"))
  intTestImplementation(project(":service:softtoken-signer:softtoken-signer-application"))
  intTestImplementation(project(":common:common-core"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":common:common-properties"))
}

intTestComposeEnv {
  images(
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "SIGNER_IMG" to "ss-signer",
    "SOFTTOKEN_SIGNER_IMG" to "ss-softtoken-signer"
  )
}

intTestShadowJar {
  archiveBaseName("softtoken-signer-int-test")
  mainClass("org.niis.xroad.softtoken.signer.test.ConsoleIntTestRunner")
}

tasks.register<Test>("intTest") {
  dependsOn(":service:signer:signer-application:quarkusBuild")
  dependsOn(":service:softtoken-signer:softtoken-signer-application:quarkusBuild")
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

archUnit {
  setSkip(true)
}
