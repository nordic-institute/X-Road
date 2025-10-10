plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

sourceSets {
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

dependencies {
  "intTestImplementation"(project(":common:common-test"))
  "intTestImplementation"(project(":common:common-int-test"))
}

intTestComposeEnv {
  images(
    "SERVERCONF_INIT_IMG" to "ss-db-serverconf-init",
    "SIGNER_IMG" to "ss-signer"
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

archUnit {
  setSkip(true)
}
