plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-scheduler"))
  implementation(project(":common:common-db"))
  implementation(project(":common:common-messagelog"))
  implementation(project(":addons:messagelog:messagelog-db"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":lib:asic-core"))

  implementation("org.springframework:spring-context-support")
}

tasks.jar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.messagelog.archiver.application.LogArchiverMain")
  }
}

tasks.shadowJar {
  archiveBaseName.set("messagelog-archiver")
  archiveVersion.set("")
  archiveClassifier.set("")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}
