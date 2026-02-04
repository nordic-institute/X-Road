plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.openapi.generator)
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
      layout.buildDirectory.dir("generated-sources/openapi/src/main/java")
    )
  }
}

openApiGenerate {
  generatorName.set("spring")
  inputSpec.set("$projectDir/src/main/resources/openapi-definition.yaml")
  outputDir.set("${layout.buildDirectory.get().asFile}/generated-sources/openapi")
  apiPackage.set("org.niis.xroad.cs.openapi")
  modelPackage.set("org.niis.xroad.cs.openapi.model")
  modelNameSuffix.set("Dto")
  templateDir.set("$projectDir/src/main/resources/template")

  importMappings.set(
    mapOf(
      "User" to "org.niis.xroad.restapi.openapi.model.User",
      "ErrorInfo" to "org.niis.xroad.restapi.openapi.model.ErrorInfo",
      "CodeWithDetails" to "org.niis.xroad.restapi.openapi.model.CodeWithDetails"
    )
  )

  globalProperties.set(
    mapOf(
      "modelDocs" to "false",
      "apis" to "", // must use empty strings instead of "true"
      "models" to "",
      "generateSupportingFiles" to "false",
      "skipDefaultInterface" to "true",
      "supportingFiles" to "ApiUtil.java"
    )
  )

  additionalProperties.set(
    mapOf(
      "generatedConstructorWithRequiredArgs" to false
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

tasks.compileJava {
  dependsOn(tasks.openApiGenerate)
}

archUnit {
  isSkip = true
}
