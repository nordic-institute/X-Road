plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(libs.edc.spi.core)                // QuerySpec, Criterion, StoreResult
    implementation(libs.edc.boot)                    // ServiceExtension, @Provider, @Inject
    implementation(libs.edc.spi.dataplane.selector) // DataPlaneInstanceStore, DataPlaneInstance
    implementation(libs.jakarta.annotationApi)
    implementation(libs.slf4j.api)

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
}
