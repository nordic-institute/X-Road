plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.openapi.generator)
  id("org.niis.xroad.oasvalidatorplugin")
}

dependencies {
  compileOnly(libs.springBoot.starterWeb)
  compileOnly(libs.swagger.parserV3)

  api(libs.swagger.annotations)
  api(libs.jakarta.validationApi)
}

sourceSets {
  main {
    java.srcDirs(
      "src/main/java",
      layout.buildDirectory.dir("generated-sources/openapi/src/main/java"),
      layout.buildDirectory.dir("generated-sources/openapi-v2/src/main/java")
    )
  }
}

openApiGenerate {
  generatorName.set("spring")
  inputSpec.set("$projectDir/src/main/resources/META-INF/openapi-definition.yaml")
  outputDir.set("${layout.buildDirectory.get().asFile}/generated-sources/openapi")
  apiPackage.set("org.niis.xroad.securityserver.restapi.openapi")
  modelPackage.set("org.niis.xroad.securityserver.restapi.openapi.model")
  modelNameSuffix.set("Dto")

  globalProperties.set(
    mapOf(
      "modelDocs" to "false",
      "apis" to "", // must use empty strings instead of "true"
      "models" to "",
      "generateSupportingFiles" to "true",
      "supportingFiles" to "ApiUtil.java"
    )
  )

  configOptions.set(
    mapOf(
      "useJakartaEe" to "true",
      "interfaceOnly" to "true",
      "useTags" to "true",
      "documentationProvider" to "none",
      "skipDefaultInterface" to "true",
      "openApiNullable" to "false"
    )
  )
}

tasks.openApiGenerate {
  inputs.files(openApiGenerate.inputSpec)
  outputs.dir(openApiGenerate.outputDir)
}

val openApiGenerateV2 by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
  generatorName.set("spring")
  inputSpec.set("$projectDir/src/main/resources/META-INF/openapi-definition-v2.yaml")
  outputDir.set("${layout.buildDirectory.get().asFile}/generated-sources/openapi-v2")
  apiPackage.set("org.niis.xroad.securityserver.restapi.openapi")
  modelPackage.set("org.niis.xroad.securityserver.restapi.openapi.model")
  modelNameSuffix.set("Dto")

  importMappings.set(
    mapOf(
      "ErrorInfo" to "org.niis.xroad.securityserver.restapi.openapi.model.ErrorInfoDto"
    )
  )

  globalProperties.set(
    mapOf(
      "modelDocs" to "false",
      "apis" to "",
      "models" to "",
      "generateSupportingFiles" to "false"
    )
  )

  configOptions.set(
    mapOf(
      "useJakartaEe" to "true",
      "interfaceOnly" to "true",
      "useTags" to "true",
      "documentationProvider" to "none",
      "skipDefaultInterface" to "true",
      "openApiNullable" to "false"
    )
  )

  inputs.files(inputSpec)
  outputs.dir(outputDir)
}

apiValidationParameters {
  apiDefinitionPaths = listOf(
    "$projectDir/src/main/resources/META-INF/openapi-definition.yaml".toString(),
    "$projectDir/src/main/resources/META-INF/openapi-authentication.yaml".toString(),
    "$projectDir/src/main/resources/META-INF/openapi-definition-v2.yaml".toString()
  )
}

tasks.named("validateApiDefinitions") {
  inputs.files(
    "src/main/resources/META-INF/openapi-definition.yaml",
    "src/main/resources/META-INF/openapi-authentication.yaml",
    "src/main/resources/META-INF/openapi-definition-v2.yaml"
  )
  outputs.upToDateWhen { true }
}

tasks.openApiGenerate {
  dependsOn("validateApiDefinitions")
}

openApiGenerateV2 {
  dependsOn("validateApiDefinitions")
}

tasks.compileJava {
  dependsOn(tasks.openApiGenerate, openApiGenerateV2)
}

archUnit {
  isSkip = true
}
