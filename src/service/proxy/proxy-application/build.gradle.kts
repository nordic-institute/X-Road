plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.logback.classic)
}

tasks.jar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.proxy.application.ProxyMain")
  }
  archiveClassifier.set("plain")
}

tasks.shadowJar {
  archiveClassifier.set("")
  archiveBaseName.set("proxy")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.assemble {
  finalizedBy(tasks.shadowJar)
}

val runProxyTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxyTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
