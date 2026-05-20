plugins {
  base
}

val otelAgent: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  isTransitive = false
}

dependencies {
  otelAgent(libs.opentelemetry.javaagent)
}

tasks.register<Sync>("stageAgent") {
  from(otelAgent)
  into(layout.buildDirectory.dir("libs"))
  rename(".*", "opentelemetry-javaagent.jar")
}

tasks.register("writeAgentVersion") {
  val versionFile = layout.buildDirectory.file("agent-versions.env")
  outputs.file(versionFile)
  val agentVersion = libs.versions.opentelemetry.javaagent.get()
  doLast {
    versionFile.get().asFile.writeText("OTEL_JAVAAGENT_VERSION=$agentVersion\n")
  }
}

tasks.named("assemble") {
  dependsOn("stageAgent", "writeAgentVersion")
}
