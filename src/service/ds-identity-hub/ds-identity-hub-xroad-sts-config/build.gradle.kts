plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.identityhub) {
    // Swagger doc-generation chain, unused here; it only resolves to a verified version with the Quarkus BOM
    // that the application module imports.
    exclude("io.swagger.core.v3", "swagger-jaxrs2-jakarta")
  }

  // Supplies the sts-api-version.json resource that the extension registers.
  testRuntimeOnly(libs.edc.ih.sts.api) {
    exclude("io.swagger.core.v3", "swagger-jaxrs2-jakarta")
  }
  testImplementation(libs.assertj.core)
  testImplementation(libs.edc.junit)
  testImplementation(libs.edc.boot)
}
