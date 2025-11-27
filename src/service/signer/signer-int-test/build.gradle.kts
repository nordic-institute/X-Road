plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.shadow)
  alias(libs.plugins.allure)
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:test-framework-core"))
  intTestImplementation(project(":service:signer:signer-client"))
  intTestImplementation(project(":common:common-core"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":common:common-properties"))
}

intTestComposeEnv {
  images(
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "SIGNER_IMG" to "ss-signer",
    "CA_IMG" to "testca-dev"
  )
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
allure {
  adapter {
    frameworks {
      cucumber7Jvm
    }
  }
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  dependsOn(provider { tasks.named("generateIntTestEnv") })
}

tasks.named<Copy>("processIntTestResources") {
  from("../../../../development/docker/testca-dev")
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  archiveBaseName.set("signer-int-test")
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
      "Main-Class" to "org.niis.xroad.signer.test.ConsoleIntTestRunner"
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
