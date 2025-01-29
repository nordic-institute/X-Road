plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(project(":service:signer:signer-core"))
  implementation(libs.logback.classic)
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  archiveClassifier.set("")
  archiveBaseName.set("signer")
  manifest {
    attributes("Main-Class" to "org.niis.xroad.signer.application.SignerMain")
  }
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
