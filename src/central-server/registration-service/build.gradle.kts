plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.springBoot)
}

tasks.bootJar {
  manifest {
    attributes(
      mapOf(
        "Implementation-Title" to "xroad-centralserver-registration-service",
        "Implementation-Version" to project.property("xroadVersion")
      )
    )
  }
}

base {
  archivesName.set("centralserver-registration-service")
}

dependencies {
  annotationProcessor(platform(libs.springBoot.bom))
  implementation(platform(libs.springBoot.bom))
  implementation(platform(libs.springCloud.bom))

  annotationProcessor("org.springframework:spring-context-indexer")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation(project(":common:common-api-throttling"))
  implementation(project(":central-server:admin-service:api-client"))
  implementation(project(":central-server:openapi-model"))
  implementation(project(":lib:globalconf-spring"))

  implementation(project(":common:common-management-request")) {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation("org.springframework.boot:spring-boot-starter-web") {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation("org.springframework.boot:spring-boot-starter-jetty")
  implementation(project(":common:common-properties"))
  implementation(libs.jakarta.validationApi)
  implementation(libs.bucket4j.core)
  implementation(libs.logback.classic)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-management-request")))
  testImplementation(testFixtures(project(":common:common-api-throttling")))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(libs.wiremock.standalone)
}
