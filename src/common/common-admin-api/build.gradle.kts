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

    implementation(libs.springBoot.starterWeb)
    implementation(libs.springBoot.starterSecurity)
    implementation(libs.springBoot.starterDataJpa)
    implementation(libs.springBoot.starterCache)
    implementation(libs.springBoot.starterValidation)
    implementation(libs.springBoot.starterActuator)
    implementation(libs.springBoot.micrometerTracingBrave)
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation(libs.libpam4j)
    implementation(libs.swagger.parserV3)
    implementation(libs.swagger.annotations)
    implementation(libs.mapstruct)

    implementation(libs.jakarta.validationApi)
    implementation(libs.apache.tikaCore)

    api("com.github.ben-manes.caffeine:caffeine")

    testImplementation(project(":common:common-test"))
    testImplementation(libs.springBoot.micrometerTracingTest)
    testImplementation(libs.springBoot.starterSecurityTest)
    testImplementation(libs.springBoot.starterJdbcTest)
    testImplementation(libs.springBoot.starterWebmvcTest)
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
    additionalProperties.put("useSpringBoot4", "true")
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
        "$projectDir/src/main/resources/common-openapi-definition.yaml"
    )
}

tasks.named("validateApiDefinitions") {
    inputs.files("src/main/resources/common-openapi-definition.yaml")
    outputs.upToDateWhen { true }
}

tasks.named("openApiGenerate") {
    dependsOn("validateApiDefinitions")
}
