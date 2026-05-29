plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":service:configuration-proxy:configuration-proxy-common"))
  implementation(project(":service:configuration-proxy:configuration-proxy-jpa"))
  implementation(project(":service:configuration-client:configuration-client-common"))
  implementation(project(":service:signer:signer-client"))
  implementation(project(":lib:properties-api"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:vault-quarkus"))

  implementation(libs.quarkus.arc)
  implementation(libs.quarkus.extension.vault)
  implementation(libs.quarkus.scheduler)
  implementation(libs.quarkus.rest.jackson)
  implementation(libs.quarkus.security)
  implementation(libs.quarkus.smallrye.openapi)
  api(libs.jakarta.validationApi)
}
