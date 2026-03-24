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

  implementation(libs.edc.spi.identityhub.participantcontext)
  implementation(libs.edc.spi.identity.did)
  implementation(libs.edc.spi.core)

  runtimeOnly(libs.edc.bom.identityhub)
  runtimeOnly(libs.edc.core.participantcontext.config)

}
