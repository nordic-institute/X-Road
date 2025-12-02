plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":central-server:admin-service:api-client"))
  intTestImplementation(testFixtures(project(":common:common-management-request")))
  intTestImplementation(project(":tool:test-framework-core"))
}

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
}

intTestShadowJar {
  archiveBaseName("central-server-management-int-test")
  mainClass("org.niis.xroad.cs.test.ConsoleIntTestRunner")
}

tasks.register<Test>("intTest") {
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

tasks.test {
  useJUnitPlatform()
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

archUnit {
  setSkip(true)
}
