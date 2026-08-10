plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":service:ds-identity-hub:ds-identity-hub-db"))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:vault-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edc.ih.common.core)
  runtimeOnly(libs.edc.ih.core)
  runtimeOnly(libs.edc.ih.did)
  runtimeOnly(libs.edc.ih.participants)
  runtimeOnly(libs.edc.ih.keypairs)
  runtimeOnly(libs.edc.ih.local.did.publisher)
  runtimeOnly(libs.edc.ih.credential.offer.handler)
  runtimeOnly(libs.edc.ih.credential.watchdog)
  runtimeOnly(libs.edc.ih.sts.account.provisioner)
  runtimeOnly(libs.edc.ih.identity.did.core)
  runtimeOnly(libs.edc.ih.identity.did.web)
  runtimeOnly(libs.edc.ih.participant.context.core)
  runtimeOnly(libs.edc.ih.version.api)
  runtimeOnly(libs.edc.dcp.core)
  runtimeOnly(libs.edc.ih.dcp.core)
  runtimeOnly(libs.edc.ih.dcp.presentation.api)
  runtimeOnly(libs.edc.ih.dcp.storage.api)
  runtimeOnly(libs.edc.ih.dcp.credential.offer.api)
  runtimeOnly(libs.edc.ih.sts.account.service.local)
  runtimeOnly(libs.edc.ih.sts.core)
  runtimeOnly(libs.edc.ih.sts.api)
  runtimeOnly(libs.edc.ih.api.core)
  runtimeOnly(libs.edc.core.token)
  runtimeOnly(libs.edc.lib.token)
  runtimeOnly(libs.edc.transaction.local)
  runtimeOnly(libs.edc.boot)
  runtimeOnly(libs.edc.core.connector)
  runtimeOnly(libs.edc.core.runtime)
  runtimeOnly(libs.edc.http) {
    // org.eclipse.edc:http pulls org.eclipse.edc:jetty-core compile-scope transitively - the
    // owned lib:edc-jetty-core replacement must be the only Jetty WebServer ServiceExtension
    // on the classpath, or the stock upstream one races it for registration and can silently
    // boot a plaintext, file-keystore-fallback Jetty instead.
    exclude(group = "org.eclipse.edc", module = "jetty-core")
  }
  runtimeOnly(libs.edc.api.observability)
  runtimeOnly(libs.edc.jsonld)
  runtimeOnly(libs.edc.configuration.filesystem)
  runtimeOnly(libs.edc.core.participantcontext.config)

  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(project(":lib:rpc-quarkus"))
  runtimeOnly(project(":service:signer:signer-client"))

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-xroad-claim"))
  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-provisioning-api"))
}

// Guards the owned Jetty module replacement: org.eclipse.edc:jetty-core must never
// resolve here, or the stock upstream JettyExtension races the owned XRoadJettyExtension for the
// WebServer registration and can silently boot a plaintext, file-keystore-fallback Jetty instead.
tasks.register("verifyNoUpstreamEdcJettyCore") {
  doLast {
    val offending = configurations.getByName("runtimeClasspath")
      .incoming.resolutionResult.allComponents
      .mapNotNull { it.moduleVersion }
      .filter { it.group == "org.eclipse.edc" && it.name == "jetty-core" }
    check(offending.isEmpty()) {
      "org.eclipse.edc:jetty-core resolved on the runtime classpath ($offending) - the owned " +
        "lib:edc-jetty-core module must be the only Jetty WebServer implementation here; " +
        "exclude jetty-core from whichever dependency reintroduced it."
    }
  }
}
tasks.named("check") { dependsOn("verifyNoUpstreamEdcJettyCore") }
