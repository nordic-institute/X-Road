plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":service:ds-control-plane:ds-control-plane-db")) {
    exclude("org.eclipse.edc", "transfer-data-plane-signaling") // deprecated
  }

  implementation(project(":service:ds-control-plane:ds-ext-sample"))
  implementation(project(":service:ds-control-plane:ds-xroad-edr-api"))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edcv.bom.controlplane) {
    exclude("org.eclipse.edc", "transfer-data-plane-signaling") // deprecated
  }
  runtimeOnly(libs.edcv.tasks.store.poll.executor)
  runtimeOnly(libs.edc.core.edrstore)
  runtimeOnly(libs.edc.edrstore.receiver)
  runtimeOnly(libs.edc.edr.cache.api)
  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(libs.edc.dataplane.signaling)
  runtimeOnly(libs.edc.contolplane.api.config)

  runtimeOnly(libs.edc.core.sql.bootstrapper)  //TODO runs DML on startup. move to different module?
  runtimeOnly(libs.bundles.edc.dcp)
}
