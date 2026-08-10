plugins {
  id("xroad.java-conventions")
}

dependencies {
  annotationProcessor(libs.hibernate.jpamodelgen)
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  api(project(":common:common-db-identifiers"))
  api(project(":central-server:admin-service:core-api"))
  api(project(":common:common-api-throttling"))

  api(libs.springBoot.starterWeb)
  api(libs.springBoot.starterSecurity)
  api(libs.springBoot.starterCache)
  api(libs.springBoot.starterValidation)
  api("org.springframework.data:spring-data-commons")
  api("jakarta.transaction:jakarta.transaction-api")
  api("jakarta.persistence:jakarta.persistence-api")

  implementation(libs.apache.commonsCompress)
  implementation(libs.mapstruct)
  implementation(project(":lib:rpc-spring"))
  implementation(project(":lib:acme-core"))
  implementation(project(":service:ds-issuer-service:ds-issuer-service-provisioning-protocol"))

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":lib:acme-core")))
  testImplementation(libs.springBoot.starterTest)
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.liquibase:liquibase-core")
  testImplementation(libs.xmlunit.core)
  testImplementation(libs.xmlunit.assertj3)
}

sourceSets {
  main {
    java.srcDirs(
      "src/main/java",
      layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main")
    )
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.add("-Amapstruct.unmappedTargetPolicy=ERROR")
}
