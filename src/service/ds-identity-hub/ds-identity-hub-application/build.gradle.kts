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
  runtimeOnly(libs.edc.http)
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
