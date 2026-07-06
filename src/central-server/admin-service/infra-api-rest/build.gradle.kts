plugins {
  id("xroad.java-conventions")
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(project(":central-server:admin-service:core-api"))
  implementation(project(":central-server:openapi-model"))
  implementation(project(":common:common-domain"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":service:signer:signer-api"))
  implementation(project(":common:common-admin-api"))

  implementation(libs.springBoot.starterSecurity)
  implementation(libs.springBoot.starterWeb)
  implementation(libs.springBoot.starterCache)
  implementation(libs.springBoot.starterValidation)
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.springBoot.starterTest)
  testImplementation("org.springframework.security:spring-security-test")
}

sourceSets {
  main {
    java.srcDirs(
      layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main")
    )
  }
}
