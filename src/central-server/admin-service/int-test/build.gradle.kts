plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

configurations {
  create("dist") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
  create("liquibaseLibs") {
    apply(plugin = "base")
  }
}

dependencies {
  intTestImplementation(project(path = ":central-server:admin-service:infra-jpa", configuration = "changelogJar"))
  intTestImplementation(project(":central-server:openapi-model"))

  intTestImplementation(libs.liquibase.core)
  intTestImplementation(libs.postgresql)
  intTestImplementation(libs.lombok)
  intTestImplementation(libs.bouncyCastle.bcpkix)
  intTestImplementation(project(":tool:test-framework-core"))
}

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
}

intTestShadowJar {
  archiveBaseName("central-server-admin-int-test")
  mainClass("org.niis.xroad.cs.test.ConsoleIntTestRunner")
}

tasks.test {
  useJUnitPlatform()
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

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

archUnit {
  setSkip(true)
}
