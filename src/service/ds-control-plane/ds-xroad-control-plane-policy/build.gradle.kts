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
    implementation(project(":lib:edc-tls-reload"))
    implementation(project(":common:common-domain"))

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.boot)
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.iam.dcp.core)
}
