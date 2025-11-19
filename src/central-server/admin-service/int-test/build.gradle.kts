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
  intTestImplementation(project(":common:common-core"))
  intTestImplementation(project(":common:common-int-test"))

  intTestImplementation(libs.bundles.testAutomation)
  intTestImplementation(libs.testAutomation.assert)
  intTestImplementation(libs.liquibase.core)
  intTestImplementation(libs.postgresql)
  intTestImplementation(libs.lombok)
}

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
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

  val intTestArgs = mutableListOf<String>()

  if (project.hasProperty("intTestTags")) {
    intTestArgs += "-Dtest-automation.cucumber.filter.tags=${project.property("intTestTags")}"
  }
  jvmArgs(intTestArgs)

  testLogging {
    showStackTraces = true
    showExceptions = true
    showCauses = true
    showStandardStreams = true
  }

  reports {
    junitXml.required.set(false) // equivalent to includeSystemOutLog = false
  }
}

archUnit {
  setSkip(true)
}
