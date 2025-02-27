plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
  alias(libs.plugins.jandex)
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

val xjc by configurations.creating

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
  }
  named("intTest") {
    resources {
      srcDir("../../../common/common-int-test/src/main/resources/")
    }
  }
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":common:common-jetty"))

  implementation(project(":lib:globalconf-impl"))
  implementation(project(":service:signer:signer-api"))

  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.scheduler)

  api(fileTree("../../../libs/pkcs11wrapper") { include("*.jar") })

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-properties")))
  testImplementation(libs.mockito.core)

  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-int-test"))
  intTestImplementation(libs.logback.classic)

  xjc(libs.bundles.jaxb)
}

val createDirs by tasks.registering {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

val xjcTask by tasks.registering {
  dependsOn(":common:common-domain:xjc", ":lib:globalconf-core:xjc")
  inputs.files(files("src/main/resources/*.xsd"))
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to xjc.asPath
      )

      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.signer.keyconf",
        "schema" to "src/main/resources/keyconf.xsd",
        "binding" to "../../../common/common-domain/src/main/resources/identifiers-bindings.xml"
      )
    }
  }
}

tasks.named("xjcTask") {
  dependsOn(createDirs)
}

tasks.compileJava {
  dependsOn(xjcTask)
}

tasks.register<Test>("intTest") {
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

tasks.named("compileIntTestJava") {
  dependsOn(tasks.named("jandex"))
}

tasks.named("check") {
  dependsOn(tasks.named("intTest"))
}
