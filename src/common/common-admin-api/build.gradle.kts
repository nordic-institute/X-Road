plugins {
    id("xroad.java-conventions")
    alias(libs.plugins.springBoot)
    alias(libs.plugins.openapi.generator)
    id("org.niis.xroad.oasvalidatorplugin")
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "build/generated-sources/openapi/src/main/java")
    }
}

dependencies {
    api(platform(libs.springBoot.bom))

    api(project(":common:common-domain"))
    implementation(project(":common:common-core"))
    implementation(project(":service:signer:signer-api"))
    implementation(project(":lib:serverconf-core"))

    annotationProcessor(libs.mapstructProcessor)
    annotationProcessor(libs.lombokMapstructBinding)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation(libs.libpam4j)
    implementation(libs.swagger.parserV3)
    implementation(libs.swagger.annotations)
    implementation(libs.mapstruct)

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.jakarta.validationApi)
    implementation(libs.apache.tikaCore)

    api("com.github.ben-manes.caffeine:caffeine")

    testImplementation(project(":common:common-test"))
    testImplementation(project(":common:common-scheduler")) //TODO this is somehow required for data.sql to be populated
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.hsqldb)

}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/common-openapi-definition.yaml")
    outputDir.set("${layout.buildDirectory.get().asFile}/generated-sources/openapi")
    apiPackage.set("org.niis.xroad.restapi.openapi")
    modelPackage.set("org.niis.xroad.restapi.openapi.model")
    globalProperties.put("modelDocs", "false")
    globalProperties.put("apis", "")
    globalProperties.put("models", "")
    globalProperties.put("generateSupportingFiles", "false")
    configOptions.put("useJakartaEe", "true")
    configOptions.put("interfaceOnly", "true")
    configOptions.put("useTags", "true")
    configOptions.put("documentationProvider", "none")
    configOptions.put("skipDefaultInterface", "true")
    configOptions.put("openApiNullable", "false")
    configOptions.put("containerDefaultToNull", "true")
}

tasks.named<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate") {
    inputs.file(inputSpec)
    outputs.dir(outputDir)
}


tasks.compileJava {
    dependsOn(tasks.named("openApiGenerate"))
}

apiValidationParameters {
    apiDefinitionPaths = listOf(
        "$projectDir/src/main/resources/common-openapi-definition.yaml".toString()
    )
}

tasks.named("validateApiDefinitions") {
    inputs.files("src/main/resources/common-openapi-definition.yaml")
    outputs.upToDateWhen { true }
}

tasks.named("openApiGenerate") {
    dependsOn("validateApiDefinitions")
}
