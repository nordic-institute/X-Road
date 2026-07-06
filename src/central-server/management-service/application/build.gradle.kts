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

  implementation(libs.springBoot.starterWeb) {
    exclude(module = "spring-boot-starter-tomcat")
  }

  implementation(libs.springBoot.starterJetty)
  implementation(libs.springBoot.starterSecurity)
  implementation(project(":common:common-db"))
  implementation(project(":lib:properties-spring"))
  implementation(libs.logback.classic)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.springBoot.starterTest)
  testImplementation(libs.springBoot.starterWebmvcTest)
  testImplementation(testFixtures(project(":common:common-api-throttling")))
}
