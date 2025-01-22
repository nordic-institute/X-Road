plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":service:op-monitor:op-monitor-core"))
}

tasks.shadowJar {
  archiveClassifier.set("")
  archiveBaseName.set("op-monitor-daemon")
  exclude("**/module-info.class")
  manifest {
    attributes("Main-Class" to "org.niis.xroad.opmonitor.application.OpMonitorDaemonMain")
  }
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}
