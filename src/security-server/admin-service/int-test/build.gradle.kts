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
  intTestImplementation(project(path = ":security-server:admin-service:infra-jpa", configuration = "changelogJar"))
  intTestImplementation(project(":security-server:openapi-model"))

  intTestImplementation(libs.bundles.testAutomation)
  intTestImplementation(libs.testAutomation.assert)
  intTestImplementation(libs.liquibase.core)
  intTestImplementation(libs.postgresql)
  intTestImplementation(libs.lombok)
}

tasks.register<Test>("intTest") {
  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()

  if (project.hasProperty("intTestTags")) {
    intTestArgs += "-Dtest-automation.cucumber.filter.tags=${project.property("intTestTags")}"
  }
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
    junitXml.required.set(false) // defaults to true
  }

  dependsOn(":security-server:admin-service:application:bootJar")
  shouldRunAfter(tasks.test)
}

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}

archUnit {
  setSkip(true)
}
