plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:vault-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edc.ih.common.core)
  runtimeOnly(libs.edc.ih.did)
  runtimeOnly(libs.edc.ih.participants)
  runtimeOnly(libs.edc.ih.keypairs)
  runtimeOnly(libs.edc.issuer.core)
  runtimeOnly(libs.edc.issuer.holders)
  runtimeOnly(libs.edc.issuer.credentials)
  runtimeOnly(libs.edc.issuer.issuance)
  runtimeOnly(libs.edc.ih.local.did.publisher)
  runtimeOnly(libs.edc.dcp.core)
  runtimeOnly(libs.edc.issuer.dcp.issuer.core)
  runtimeOnly(libs.edc.issuer.dcp.issuer.api)
  runtimeOnly(libs.edc.issuer.issuance.rules)
  runtimeOnly(libs.edc.issuer.holder.attestations)
  runtimeOnly(libs.edc.ih.sts.account.provisioner)
  runtimeOnly(libs.edc.ih.identity.did.core)
  runtimeOnly(libs.edc.ih.identity.did.web)
  runtimeOnly(libs.edc.ih.participant.context.core)
  runtimeOnly(libs.edc.ih.version.api)
  runtimeOnly(libs.edc.ih.sts.account.service.local)
  runtimeOnly(libs.edc.ih.sts.core)
  runtimeOnly(libs.edc.ih.sts.api)
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

  runtimeOnly(libs.edc.issuer.sql.holder)
  runtimeOnly(libs.edc.issuer.sql.attestation.definition)
  runtimeOnly(libs.edc.issuer.sql.credential.definition)
  runtimeOnly(libs.edc.ih.sql.credentials)
  runtimeOnly(libs.edc.issuer.sql.issuance.process)
  runtimeOnly(libs.edc.issuer.database.attestations)
  runtimeOnly(libs.edc.ih.sql.did)
  runtimeOnly(libs.edc.ih.sql.keypair)
  runtimeOnly(libs.edc.ih.sql.sts.client)
  runtimeOnly(libs.edc.sql.core)
  runtimeOnly(libs.edc.sql.pool)
  runtimeOnly(libs.edc.core.sql.bootstrapper)
  runtimeOnly(libs.edc.sql.jti.validation)
  runtimeOnly(libs.edc.sql.lease.core)
  runtimeOnly(libs.edc.sql.participantcontext.store)
  runtimeOnly(libs.edc.store.participantcontext.config.sql)
  runtimeOnly(libs.postgresql)

  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(project(":lib:rpc-quarkus"))
  implementation(project(":lib:globalconf-core"))

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-provisioning-api"))
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
