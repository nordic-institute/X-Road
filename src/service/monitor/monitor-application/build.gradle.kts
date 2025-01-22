plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(project(":service:monitor:monitor-core"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.bundles.metrics)
  implementation("org.springframework:spring-context")
}

tasks.shadowJar {
  exclude("**/module-info.class")
  archiveBaseName.set("monitor")
  archiveClassifier.set("")
  manifest {
    attributes("Main-Class" to "org.niis.xroad.monitor.application.MonitorMain")
  }
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}
