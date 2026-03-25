plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(libs.edc.spi.policy.engine)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.catalog)

    implementation(project(":lib:globalconf-core"))
    implementation(project(":lib:serverconf-core"))
    implementation(project(":common:common-domain"))

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.jupiter)
}
