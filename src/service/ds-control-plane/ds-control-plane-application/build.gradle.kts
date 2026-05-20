plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":service:ds-control-plane:ds-control-plane-db"))

  implementation(project(":service:ds-control-plane:ds-ext-sample"))
  implementation(project(":service:ds-control-plane:ds-xroad-asset-access-api"))
  implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))
  implementation(project(":service:ds-control-plane:ds-xroad-assets"))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":lib:vault-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edc.core.controlplane) {
    // EDC 0.17 ships two mutually-exclusive state-machine drivers: classic Manager
    // (control-plane-{contract,transfer}-manager) and task-based (control-plane-
    // {contract,transfer}-task-executor). control-plane-core transitively pulls
    // the Manager modules. We use the task-based driver (TaskPollExecutor below),
    // so the Manager modules are excluded to prevent dual state-machine execution
    // (duplicate state transitions, duplicate protocol message sends, 409s on
    // verification/finalization). See .scratch/dsp-rest-integration/issues/02.
    exclude("org.eclipse.edc", "control-plane-contract-manager")
    exclude("org.eclipse.edc", "control-plane-transfer-manager")
  }
  runtimeOnly(libs.edc.core.controlplane.contract.tasks)
  runtimeOnly(libs.edc.core.controlplane.transfer.tasks)
  runtimeOnly(libs.edc.core.tasks)
  runtimeOnly(libs.edc.core.dsp.virtual)
  runtimeOnly(libs.edc.core.dps)
  runtimeOnly(libs.edc.core.dps.oauth)
  runtimeOnly(libs.edc.mgmtapi.v5)
  runtimeOnly(libs.edc.core.mgmtapi.jsonschema)
  runtimeOnly(libs.edc.mgmtapi.authn.oauth2)
  runtimeOnly(libs.edc.mgmtapi.authz)
  runtimeOnly(libs.edc.core.connector)
  runtimeOnly(libs.edc.core.cel)
  runtimeOnly(libs.edc.core.runtime)
  runtimeOnly(libs.edc.core.token)
  runtimeOnly(libs.edc.core.jersey)
  runtimeOnly(libs.edc.core.jetty)
  runtimeOnly(libs.edc.core.policy.monitor)
  runtimeOnly(libs.edc.api.observability)
  runtimeOnly(libs.edc.core.dataplane.selector)
  runtimeOnly(libs.edc.encryption.aes)
  runtimeOnly(libs.edc.configuration.filesystem)

  runtimeOnly(project(":service:ds-control-plane:ds-control-plane-tasks-store-poll-executor"))
  runtimeOnly(libs.edc.core.edrstore)
  runtimeOnly(libs.edc.edrstore.receiver)
  runtimeOnly(libs.edc.edr.cache.api)
  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(libs.edc.dataplane.signaling)
  runtimeOnly(libs.edc.dataplane.signaling.client)
  runtimeOnly(libs.edc.dataplane.selector.control.api)
  runtimeOnly(libs.edc.core.participantcontext.connector.classic)
  runtimeOnly(libs.edc.contolplane.api.config)
  runtimeOnly(libs.edc.api.secrets)

  runtimeOnly(libs.edc.core.sql.bootstrapper)  //TODO runs DML on startup. move to different module?
  // Inlined virtual-controlplane-feature-dcp-bom contents (DCP bundle)
  runtimeOnly(libs.bundles.edc.dcp)
}
