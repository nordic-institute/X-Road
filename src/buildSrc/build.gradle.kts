plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

dependencies {
  implementation(platform(libs.springBoot.bom)) // Used by license plugin to fetch latest compatible version

  implementation(libs.commons.codec)
  implementation(libs.openapi.parser)
  implementation(libs.openapi.styleValidatorLib)
  implementation(libs.swagger.parser)
  implementation(libs.openapi.empoaSwaggerCore)
  implementation(libs.lombok)

  implementation(libs.licenseGradlePlugin)
  implementation(libs.archUnitGradlePlugin)

  testImplementation(libs.junit.jupiterEngine)
}

gradlePlugin {
  plugins {
    create("simplePlugin") {
      id = "org.niis.xroad.oasvalidatorplugin"
      implementationClass = "org.niis.xroad.oasvalidatorplugin.Oas3ValidatorGradlePlugin"
    }
  }
}
