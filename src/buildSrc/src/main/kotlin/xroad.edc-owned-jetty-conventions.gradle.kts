import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// EDC's own jetty-core module reads its HTTPS keystore from a file at boot with no reload seam and,
// per its own extension, falls back to plaintext HTTP whenever no keystore is configured — exactly the
// silent fallback the X-Road-owned Jetty module (lib:edc-jetty-tls) exists to rule out. Any project
// applying this convention must never let the stock artifact resolve, on any configuration, so the two
// modules cannot race to bind the same connectors.
private val stockJettyCoreGroup = "org.eclipse.edc"
private val stockJettyCoreModule = "jetty-core"

// Scoped to the classpath configurations rather than configurations.all: some configurations this
// project's other conventions create (e.g. the mockito Java-agent classpath) are resolved as a side
// effect of applying earlier plugins, and Gradle forbids adding exclude rules to an already-resolved
// configuration. Matched case-insensitively: the main source set's configurations are named
// "compileClasspath"/"runtimeClasspath" (lowercase first letter), while source-set-prefixed ones like
// "testRuntimeClasspath" capitalize it.
configurations.matching {
  val name = it.name.lowercase()
  name.endsWith("compileclasspath") || name.endsWith("runtimeclasspath")
}.configureEach {
  exclude(group = stockJettyCoreGroup, module = stockJettyCoreModule)
}

val verifyNoStockEdcJettyCore by tasks.registering {
  group = "verification"
  description = "Fails the build if $stockJettyCoreGroup:$stockJettyCoreModule resolves on the runtime classpath."

  val runtimeClasspath = configurations.named("runtimeClasspath")
  inputs.files(runtimeClasspath)

  doLast {
    val offending = runtimeClasspath.get().incoming.artifacts.artifacts
      .map { it.id.componentIdentifier }
      .filterIsInstance<ModuleComponentIdentifier>()
      .filter { it.group == stockJettyCoreGroup && it.module == stockJettyCoreModule }

    if (offending.isNotEmpty()) {
      throw GradleException(
        "$stockJettyCoreGroup:$stockJettyCoreModule resolved on ${project.path}'s runtime classpath ($offending) " +
          "despite the exclusion declared by xroad.edc-owned-jetty-conventions. The X-Road-owned Jetty module " +
          "(lib:edc-jetty-tls) must be the only one serving EDC HTTPS on this classpath."
      )
    }
  }
}

tasks.named("check") {
  dependsOn(verifyNoStockEdcJettyCore)
}
