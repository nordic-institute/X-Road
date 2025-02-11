plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
    resources.srcDirs("../../../common/common-domain/src/main/resources")
  }
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-jetty"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":addons:proxymonitor-common"))

  implementation(libs.guava)

  testImplementation(project(":common:common-test"))
  testImplementation(project(path = "::service:proxy:proxy-application", configuration = "testArtifacts"))
  testImplementation(libs.hamcrest)

  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
}

tasks.register("createDirs") {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  archiveClassifier.set("")
  exclude("**/module-info.class")
  dependencies {
    include(project(":addons:proxymonitor-common"))
    include(project(":service:monitor:monitor-api"))
  }
  mergeServiceFiles()
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.compileJava {
  dependsOn(tasks.processResources)
}

tasks.register<JavaExec>("runProxymonitorMetaserviceTest") {
  // empty task for pipelines backwards compatibility. can be removed after 7.9 release.
  group = "verification"
  logger.warn("WARNING: The 'runProxymonitorMetaserviceTest' task is deprecated and does nothing. It will be removed in the future versions.")
  enabled = false
}
