# Custom Gradle Plugins in X-Road

### OpenAPI 3 Validator Gradle Plugin

A validator that validates given API definition against the OpenAPI 3 specification. The validator also checks the document for style errors.

##### Usage

1. Apply the plugin in the `build.gradle` file of the wanted X-Road sub project (e.g. `proxy-ui-api`).
2. Provide the plugin with the path(s) of the API definition document(s) in an array. Please use an array even if there is only one document to be validated.
3. Select the task that should depend on the validation.

Example configuration of the plugin as done in `proxy-ui-api` module:
```groovy
apply plugin: Oas3ValidatorGradlePlugin

...

apiValidationParameters.apiDefinitionPaths = [
    "$projectDir/src/main/resources/openapi-definition.yaml".toString(),
    "$projectDir/src/main/resources/openapi-authentication.yaml".toString()
]

tasks.openApiGenerate.dependsOn 'validateApiDefinitions'
```
