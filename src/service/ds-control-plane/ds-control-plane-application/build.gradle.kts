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

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edc.dataplane.signaling)
  runtimeOnly(libs.edc.contolplane.api.config)

  runtimeOnly(libs.edc.virtual.controlplane.feature.sql.bom) {
    exclude("org.eclipse.edc", "transfer-data-plane-signaling") // deprecated
  }
  runtimeOnly(libs.edc.spi.dataplane)
  runtimeOnly(libs.edc.spi.jsonld)
  runtimeOnly(libs.edc.spi.transaction.datasource)
  runtimeOnly(libs.edc.spi.controlplane)
  runtimeOnly(libs.edc.spi.participantcontext.config)
  runtimeOnly(libs.edc.lib.jsonld)
  runtimeOnly(libs.edc.lib.sql)

  runtimeOnly(libs.edc.core.sql.bootstrapper)  //TODO runs DML on startup. move to different module?
  runtimeOnly(libs.bundles.edc.dcp)
  runtimeOnly(libs.edc.virtual.controlplane.feature.dcp.bom)
}
