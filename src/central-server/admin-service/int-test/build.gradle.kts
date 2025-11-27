plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.shadow)
  alias(libs.plugins.allure)
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

tasks.jar {
  enabled = false
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

tasks.shadowJar {
  archiveBaseName.set("central-server-admin-int-test")
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
