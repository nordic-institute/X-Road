plugins {
    id("xroad.java-conventions")
    id("xroad.int-test-conventions")
}

dependencies {
    intTestImplementation(project(":tool:api-test-core"))
    intTestImplementation(project(":security-server:openapi-model"))
    intTestImplementation(libs.bouncyCastle.bcpkix)
    intTestImplementation(libs.junit.jupiter.params)
}

intTestComposeEnv {
    env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
    env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

    images(
        "OPENBAO_DEV_IMG" to "openbao-dev",
        "POSTGRES_DEV_IMG" to "postgres-dev",
        "CA_IMG" to "testca-dev",
        "DB_INIT_IMG" to "ss-db-init",
        "CONFIGURATION_CLIENT_IMG" to "ss-configuration-client",
        "MONITOR_IMG" to "ss-monitor",
        "SIGNER_IMG" to "ss-signer",
        "PROXY_IMG" to "ss-proxy",
        "PROXY_UI_IMG" to "ss-proxy-ui-api",
        "AUXILIARY_SERVICE_IMG" to "ss-auxiliary-service",
        "OP_MONITOR_IMG" to "ss-op-monitor",
        "DS_CONTROL_PLANE_IMG" to "ds-control-plane",
        "DS_IDENTITY_HUB_IMG" to "ds-identity-hub",
        "DS_ISSUER_SERVICE_IMG" to "ds-issuer-service"
    )
}

val copyMainComposeFile by tasks.registering(Copy::class) {
    description = "Copies main compose.yaml and required files to build directory"
    group = "verification"

    from("../../../development/docker/security-server/compose.yaml") {
        rename { "compose.main.yaml" }
    }
    into("build/resources/intTest")
}

intTestPhasedSuite {
    phasedSuiteClass = "SsApiPhasedSuite"
    productName = "Security Server"
}

intTestShadowJar {
    archiveBaseName("security-server-api-test")
    mainClass("org.niis.xroad.ss.test.api.ConsoleApiTestRunner")
}

tasks.named("shadowJar") {
    dependsOn(copyMainComposeFile)
}
