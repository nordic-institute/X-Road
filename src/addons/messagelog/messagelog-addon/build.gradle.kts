plugins {
  id("xroad.java-conventions")
}

tasks.jar {
  archiveVersion.set("")

  val messagelogDb = project(":addons:messagelog:messagelog-db")
  messagelogDb.pluginManager.withPlugin("java") {
    from(messagelogDb.extensions.getByType<JavaPluginExtension>().sourceSets.main.get().output)
  }
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":common:common-jetty"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:asic-core"))
  implementation(project(":addons:messagelog:messagelog-db"))

  testImplementation(project(":common:common-test"))
  implementation(project(":lib:keyconf-api"))
  testImplementation(project(":service:message-log-archiver:message-log-archiver-application"))
  testImplementation(libs.hsqldb)
  testImplementation(libs.bouncyCastle.bcpg)
}

tasks.register<Copy>("copyGpg") {
  description = "Copy GPG keys to build directory"
  group = "build"

  from("src/test/gpg")
  into(layout.buildDirectory.dir("gpg"))
}

tasks.test {
  dependsOn("copyGpg")
}
