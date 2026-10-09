plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.dsp.v2025)

    implementation(project(":lib:globalconf-core"))
    implementation(project(":lib:serverconf-core"))
    implementation(project(":common:common-domain"))

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
    testImplementation(libs.awaitility)
    testImplementation(libs.edc.boot)
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.core.connector)
    testImplementation(libs.edc.lib.core)
}
