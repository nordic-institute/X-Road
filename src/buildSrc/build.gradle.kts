plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

dependencies {
  implementation(platform(libs.springBoot.bom)) // Aligns transitive dependency versions; spring-core pinned separately for license plugin compat

  implementation(libs.commons.codec)
  implementation(libs.openapi.parser)
  implementation(libs.openapi.styleValidatorLib)
  implementation(libs.swagger.parser)
  implementation(libs.openapi.empoaSwaggerCore)
  implementation(libs.lombok)

  implementation(libs.licenseGradlePlugin) {
    exclude(group = "org.springframework", module = "spring-core")
  }
  implementation("org.springframework:spring-core") {
    version {
      strictly("6.2.17")
    }
    because("license-gradle-plugin 0.16.1 uses mycila 3.0 which calls PropertyPlaceholderHelper(String,String,String,boolean) — a constructor removed in Spring Framework 7")
  }
  implementation(libs.archUnitGradlePlugin)
  implementation(libs.protobufGradlePlugin)
  implementation(libs.quarkusGradlePlugin)
  implementation(libs.shadowGradlePlugin)

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
