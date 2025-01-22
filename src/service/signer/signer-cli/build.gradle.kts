plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(libs.commons.cli)
  implementation(libs.cliche)

  implementation(project(":common:common-domain"))
  implementation(project(":service:signer:signer-client"))
}

val mainClassName = "org.niis.xroad.signer.cli.SignerCLI"

tasks.jar {
  manifest {
    attributes("Main-Class" to mainClassName)
  }
}

tasks.shadowJar {
  archiveBaseName.set("signer-console")
  archiveClassifier.set("")
  exclude("**/module-info.class")
  exclude("asg/cliche/example/**")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("runSignerConsole") {
  jvmArgs("-Djava.library.path=../passwordstore")
  mainClass.set("org.niis.xroad.signer.cli.SignerCLI")
  classpath = sourceSets.test.get().runtimeClasspath
  standardInput = System.`in`
}
