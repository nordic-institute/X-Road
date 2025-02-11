plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.logback.classic)

  testImplementation(libs.hsqldb)
  testImplementation(libs.restAssured)
  testImplementation(libs.apache.httpasyncclient)
  testImplementation(project(":common:common-domain"))
  testImplementation(project(":common:common-jetty"))
  testImplementation(project(":common:common-message"))
  testImplementation(project(":common:common-test"))

  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
  testImplementation(project(":common:common-scheduler"))
  testImplementation(project(":common:common-messagelog"))
  testImplementation(project(":service:op-monitor:op-monitor-api"))
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

val testJar by tasks.registering(Jar::class) {
  archiveClassifier.set("test")
  from(sourceSets.test.get().output)
}

configurations {
  create("testArtifacts") {
    extendsFrom(configurations.testRuntimeOnly.get())
  }
}

artifacts {
  add("testArtifacts", testJar)
}

tasks.assemble {
  finalizedBy(tasks.shadowJar)
}

tasks.withType<Test> {
  jvmArgs("-Xmx2G")
}

val runProxyTest by tasks.registering(JavaExec::class) {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxyTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
