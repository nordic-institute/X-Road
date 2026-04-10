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

  runtimeOnly(project(":service:ds-identity-hub:ds-identity-hub-customization"))
}
