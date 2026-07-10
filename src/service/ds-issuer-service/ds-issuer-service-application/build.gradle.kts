plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-edc-quarkus"))
  implementation(project(":lib:properties-quarkus"))

  implementation(libs.bundles.quarkus.containerized)

  runtimeOnly(libs.edc.bom.issuerservice) {
    exclude(group = "org.eclipse.edc", module = "participant-context-api")
    exclude(group = "org.eclipse.edc", module = "issuer-admin-api")
    exclude(group = "org.eclipse.edc", module = "issuer-admin-api-configuration")
    exclude(group = "org.eclipse.edc", module = "identity-api-configuration")
    exclude(group = "org.eclipse.edc", module = "did-api")
    exclude(group = "org.eclipse.edc", module = "identity-api-authentication-oauth2")
    exclude(group = "org.eclipse.edc", module = "identity-api-authorization-oauth2")
    exclude(group = "org.eclipse.edc", module = "issuer-admin-api-authentication-oauth2")
    exclude(group = "org.eclipse.edc", module = "issuer-admin-api-authorization-oauth2")
  }
  runtimeOnly(libs.edc.core.participantcontext.config)
  runtimeOnly(libs.edc.bom.issuerservice.sql)

  runtimeOnly(libs.edc.core.sql.bootstrapper)

  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(project(":lib:rpc-quarkus"))
  implementation(project(":lib:globalconf-core"))

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-provisioning-api"))
}
