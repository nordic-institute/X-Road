plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.springBoot)
}

base {
  archivesName.set("centralserver-management-service")
}

tasks.jar {
  enabled = false
}

tasks.bootJar {
  enabled = true
  manifest {
    attributes(
      mapOf(
        "Implementation-Title" to "X-Road Central Server Management Service",
        "Implementation-Version" to project.property("xroadVersion")
      )
    )
  }
}

dependencies {
  implementation(platform(libs.springCloud.bom))

  implementation(project(":central-server:management-service:core")) {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation(project(":central-server:management-service:infra-api-soap")) {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation("org.springframework.boot:spring-boot-starter-web") {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation("org.springframework.boot:spring-boot-starter-jetty")
  implementation(project(":lib:properties-spring"))
  implementation(libs.logback.classic)

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(testFixtures(project(":common:common-api-throttling")))
}
