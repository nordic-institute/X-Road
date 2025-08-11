plugins {
  id("xroad.java-conventions")
}

configurations {
  create("liquibaseLibs") {
    apply(plugin = "base")
  }
}

dependencies {
  annotationProcessor(libs.hibernate.jpamodelgen)
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  api(project(":central-server:admin-service:core-api"))
  api(project(":common:common-api-throttling"))

  api("org.springframework.boot:spring-boot-starter-web")
  api("org.springframework.boot:spring-boot-starter-security")
  api("org.springframework.boot:spring-boot-starter-cache")
  api("org.springframework.boot:spring-boot-starter-validation")
  api("org.springframework.data:spring-data-commons")
  api("jakarta.transaction:jakarta.transaction-api")
  api("jakarta.persistence:jakarta.persistence-api")

  implementation(libs.apache.commonsCompress)
  implementation(libs.mapstruct)

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.liquibase:liquibase-core")
  testImplementation(libs.xmlunit.core)
  testImplementation(libs.xmlunit.assertj3)

  testRuntimeOnly(libs.junit.platform.launcher)
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
