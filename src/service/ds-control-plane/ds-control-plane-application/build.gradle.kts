plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":service:ds-control-plane:ds-ext-sample"))

  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  implementation(libs.edc.virtual.controlplane.feature.sql.bom)
  implementation(libs.edc.spi.dataplane)
  implementation(libs.edc.spi.jsonld)
  implementation(libs.edc.spi.transaction.datasource)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.participantcontext.config)
  implementation(libs.edc.lib.jsonld)
  implementation(libs.edc.lib.sql)

  implementation(libs.edc.core.sql.bootstrapper)  //TODO runs DML on startup. move to different module?
  implementation(libs.bundles.edc.dcp)
}
