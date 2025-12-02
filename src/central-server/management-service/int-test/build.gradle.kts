plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.shadow)
  alias(libs.plugins.allure)
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":central-server:admin-service:api-client"))
  intTestImplementation(testFixtures(project(":common:common-management-request")))
  intTestImplementation(project(":tool:test-framework-core"))
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

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
}

tasks.test {
  useJUnitPlatform()
}

allure {
  adapter {
    frameworks {
      cucumber7Jvm
    }
  }
}

tasks.jar {
  enabled = false
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

tasks.shadowJar {
  archiveBaseName.set("central-server-management-int-test")
  archiveClassifier.set("")
  isZip64 = true

  from(sourceSets["intTest"].output.classesDirs)

  from("${layout.buildDirectory.get().asFile}/resources/intTest") {
    into("")
  }
  from("${layout.buildDirectory.get().asFile}/resources/intTest/.env") {
    into("")
  }
  from(sourceSets["intTest"].runtimeClasspath.filter { it.name.endsWith(".jar") })

  mergeServiceFiles()
  exclude("**/module-info.class")

  manifest {
    attributes(
      "Main-Class" to "org.niis.xroad.cs.test.ConsoleIntTestRunner"
    )
  }

  dependsOn(provider { tasks.named("generateIntTestEnv") })
  dependsOn(tasks.named("intTestClasses"))
  dependsOn(tasks.named("processIntTestResources"))
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
archUnit {
  setSkip(true)
}
