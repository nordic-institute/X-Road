plugins {
    id("xroad.java-conventions")
    id("xroad.int-test-conventions")
}

dependencies {
    intTestImplementation(project(":tool:api-test-core"))
    intTestImplementation(project(":central-server:openapi-model"))
    intTestImplementation(libs.postgresql)
    intTestImplementation(libs.junit.jupiter.params)
    intTestImplementation(libs.bouncyCastle.bcpkix)
}

intTestComposeEnv {
    images(
        "CS_IMG" to "central-server-dev"
    )
}

intTestPhasedSuite {
    phasedSuiteClass = "CsApiPhasedSuite"
    productName = "Central Server"
}

intTestShadowJar {
    archiveBaseName("central-server-api-test")
    mainClass("org.niis.xroad.cs.test.api.ConsoleApiTestRunner")
}

archUnit {
    setSkip(true)
}
