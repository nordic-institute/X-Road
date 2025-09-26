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


tasks.register<Test>("intTest") {
  dependsOn(":service:signer:signer-application:quarkusBuild")

  useJUnitPlatform()

  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  val intTestArgs = mutableListOf<String>()

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
    junitXml.required.set(false)
  }
}

archUnit {
  setSkip(true)
}
