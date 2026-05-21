plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(libs.edc.spi.asset)               // AssetIndex, DataAddressResolver
    implementation(libs.edc.spi.policy)              // PolicyDefinitionStore
    implementation(libs.edc.spi.contract)            // ContractDefinitionStore
    implementation(libs.edc.spi.core)                // QuerySpec, Criterion, StoreResult
    implementation(libs.edc.boot)                    // ServiceExtension, @Provider, @Inject
    implementation(libs.edc.spi.policy.engine)       // ODRL model types
    implementation(libs.edc.spi.dataplane.http)      // HttpDataAddress.Builder

    implementation(project(":lib:serverconf-core"))  // ServerConfProvider
    implementation(project(":lib:globalconf-core"))  // GlobalConfProvider
    implementation(project(":common:common-domain")) // X-Road domain types
    implementation(project(":service:ds-control-plane:ds-xroad-control-plane-policy"))

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
    testImplementation(libs.logback.classic)  // Logback ListAppender for WARN log assertions
}
