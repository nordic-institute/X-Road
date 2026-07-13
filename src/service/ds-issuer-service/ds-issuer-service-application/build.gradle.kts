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

  runtimeOnly(libs.edc.bom.issuerservice)
  runtimeOnly(libs.edc.bom.issuerservice.sql)

  runtimeOnly(libs.edc.core.sql.bootstrapper)

  runtimeOnly(libs.edc.vault.hashicorp)

  runtimeOnly(project(":lib:rpc-quarkus"))
  implementation(project(":lib:globalconf-core"))

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-customization"))
  runtimeOnly(project(":service:ds-issuer-service:ds-issuer-service-provisioning-api"))
}
