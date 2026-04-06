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

  runtimeOnly(libs.edc.bom.identityhub)
  runtimeOnly(libs.edc.core.participantcontext.config)

  runtimeOnly(libs.edc.vault.hashicorp)

  // Following dependencies are needed to introduce XRoadIdentityHubParticipantContextService
  implementation(libs.edc.ih.participants)
  implementation(libs.edc.spi.participantcontext.config)
}
