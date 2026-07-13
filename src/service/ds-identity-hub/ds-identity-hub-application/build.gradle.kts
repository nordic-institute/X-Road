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

  runtimeOnly(libs.edc.bom.identityhub) {
    exclude(group = "org.eclipse.edc", module = "identity-api-configuration")
    exclude(group = "org.eclipse.edc", module = "participant-context-api")
    exclude(group = "org.eclipse.edc", module = "did-api")
    exclude(group = "org.eclipse.edc", module = "verifiable-credentials-api")
    exclude(group = "org.eclipse.edc", module = "keypair-api")
    exclude(group = "org.eclipse.edc", module = "identity-api-authentication-oauth2")
    exclude(group = "org.eclipse.edc", module = "identity-api-authorization-oauth2")
  }
  runtimeOnly(libs.edc.core.participantcontext.config)

  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(project(":lib:rpc-quarkus"))
  runtimeOnly(project(":service:signer:signer-client"))

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-xroad-claim"))
  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-provisioning-api"))
}
