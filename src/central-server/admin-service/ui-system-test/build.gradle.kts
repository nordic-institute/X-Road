plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":central-server:openapi-model"))
  intTestImplementation(project(":tool:test-framework-core"))

  intTestImplementation(libs.bouncyCastle.bcpkix)
}

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
}

intTestShadowJar {
  archiveBaseName("central-server-system-test")
  mainClass("org.niis.xroad.cs.test.ui.ConsoleIntTestRunner")
}

tasks.register<Test>("intTest") {
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
