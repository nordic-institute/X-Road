plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.web)
  implementation(libs.edc.spi.auth)
  implementation(libs.edc.spi.dataplane.selector)
  implementation(libs.edc.spi.controlplane)
  implementation(libs.edc.spi.participantcontext)
  implementation(libs.edc.spi.transform)
  implementation(libs.edc.core.dps) {
    // Swagger/OpenAPI doc-generation dependency, only needed by the module's own edcBuild plugin at build time;
    // not required to reference the (already-compiled) controller/transformer classes we reuse here. Pulling it
    // in also drags an unpinned jakarta.validation-api/opentelemetry transitive chain that only resolves to a
    // verified version when the Quarkus BOM (imported at the application module) is on the classpath.
    exclude("io.swagger.core.v3", "swagger-jaxrs2-jakarta")
  }
  implementation(libs.jakarta.annotationApi)
  implementation(libs.slf4j.api)

  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.jupiter)
}
