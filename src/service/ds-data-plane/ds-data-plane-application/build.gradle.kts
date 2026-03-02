plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":service:ds-data-plane:ds-data-plane-db"))

  runtimeOnly(project(":service:ds-data-plane:ds-xroad-data-plane"))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))

  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  runtimeOnly(libs.edc.bom.dataplane) {
    exclude("org.eclipse.edc", "data-plane-self-registration")
    exclude("org.eclipse.edc", "data-plane-signaling-api")
  }
  runtimeOnly(libs.edc.core.participantcontext.config)
}
