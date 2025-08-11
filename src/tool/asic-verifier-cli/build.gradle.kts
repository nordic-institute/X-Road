plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))

  testImplementation(project(":common:common-test"))

  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.asic.verifier.cli.AsicVerifierMain")
  }
  archiveBaseName.set("asicverifier")
  archiveClassifier.set("")
  archiveVersion.set("")
  exclude("**/module-info.class")
}

tasks.build {
  finalizedBy(tasks.shadowJar)
}
