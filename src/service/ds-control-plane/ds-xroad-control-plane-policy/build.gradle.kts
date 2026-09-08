plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(libs.edc.spi.policy.engine)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.participant)
    implementation(libs.edc.spi.verifiablecredentials)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.dsp.v2025)

    implementation(project(":lib:globalconf-core"))
    implementation(project(":lib:serverconf-core"))
    implementation(project(":common:common-domain"))

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
}
