plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(project(":lib:properties-core"))

    api(libs.slf4j.api)
    api(libs.logback.classic)
    api(libs.julOverSlf4j)

    api(libs.smallrye.config.core)
    api(libs.smallrye.config.yaml)

    api(libs.junit.jupiterEngine)
    api(libs.junit.platform.console)
    api(libs.junit.platform.suiteApi)
    api(libs.junit.platform.suiteEngine)

    api(libs.testcontainers.core)

    api(libs.test.allure.junitPlatform)
    api(libs.test.allure.attachments)
    api(libs.test.allure.commandline) {
        exclude(group = "ru.qatools.commons")
    }

    api(libs.test.restassured)
    api(libs.awaitility)

    api(libs.assertj.core)

    api(libs.jackson.annotations)
    api(libs.apache.commonsCompress)
}

archUnit {
    setSkip(true)
}
