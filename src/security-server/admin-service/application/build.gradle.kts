plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.springBoot)
}

base {
  archivesName.set("proxy-ui-api")
}

configurations {
  create("dist") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
}

configurations.configureEach {
  if (name != "mockitoAgent") {
    exclude(module = "jetty-jakarta-servlet-api")
  }
}

dependencies {
  add("dist", project(path = ":security-server:admin-service:ui", configuration = "dist"))

  implementation(platform(libs.springBoot.bom))
  implementation(platform(libs.springCloud.bom))

  implementation(project(":lib:globalconf-spring"))
  implementation(project(":lib:acme-core"))
  implementation(project(":service:signer:signer-client-spring"))
  implementation(project(":lib:serverconf-spring"))
  implementation(project(":lib:rpc-spring"))
  implementation(project(":common:common-admin-api"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-management-request"))
  implementation(project(":common:common-api-throttling"))
  implementation(project(":common:common-pgp"))
  implementation(project(":lib:properties-spring"))
  implementation(project(":security-server:openapi-model"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:op-monitor:op-monitor-client"))
  implementation(project(":service:auxiliary-service:auxiliary-service-rpc-client"))
  implementation(project(":service:configuration-client:configuration-client-rpc-client"))
  implementation(project(":service:monitor:monitor-rpc-client"))
  implementation(project(":service:proxy:proxy-rpc-client"))
  implementation(project(":service:ds-identity-hub:ds-identity-hub-provisioning-protocol"))
  implementation(project(":service:ds-control-plane:ds-xroad-provisioning-protocol"))

  implementation(libs.springBoot.starterSecurity)
  implementation(libs.springBoot.starterWeb)
  implementation(libs.springBoot.starterDataJpa)
  implementation(libs.springBoot.starterCache)
  implementation(libs.springBoot.starterMail)
  implementation(libs.springBoot.starterValidation)
  implementation(libs.libpam4j)
  implementation(libs.apache.commonsCompress)
  implementation(libs.wsdl4j)
  implementation(libs.bucket4j.core)
  implementation(libs.swagger.parserV3)
  implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
  implementation(libs.jakarta.validationApi)
  implementation(libs.logback.classic)
  implementation(libs.logback.access) {
    exclude(group = "org.apache.tomcat")
  }

  implementation(libs.apache.cxfToolsValidator)
  implementation(libs.apache.cxfRtTransportsHttp)
  implementation(libs.javax.annotationApi)

  testImplementation(platform(libs.springBoot.bom))
  testImplementation(project(":common:common-test"))
  testImplementation(libs.springBoot.starterJdbcTest)
  testImplementation(libs.springBoot.starterWebmvcTest)
  testImplementation(libs.springBoot.starterWebfluxTest)
  testImplementation(libs.springBoot.starterSecurityTest)
  testImplementation(libs.hsqldb)
  testImplementation(libs.jsonUnit.assertj)
  testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
  testImplementation(testFixtures(project(":common:common-api-throttling")))
  testImplementation(libs.systemStubs)
  testRuntimeOnly(libs.junit.vintageEngine)
}

tasks.register<Copy>("copyUi") {
  dependsOn(configurations["dist"])
  from(configurations["dist"])
  into(layout.buildDirectory.dir("resources/main/public"))
}

tasks.named("resolveMainClassName") {
  dependsOn(tasks.named("copyUi"))
}
tasks.named("compileTestJava") {
  dependsOn(tasks.named("copyUi"))
}

tasks.bootRun {
  jvmArgs = listOf("-Dspring.output.ansi.enabled=ALWAYS")
  if (project.hasProperty("args")) {
    args = project.property("args").toString().split(",")
  }
}

tasks.jar {
  enabled = false
}

tasks.bootJar {
  enabled = true

  manifest {
    attributes(
      mapOf(
        "Implementation-Title" to "X-Road Security Server Admin Service",
        "Implementation-Version" to "${project.property("xroadVersion")}-${project.property("xroadBuildType")}"
      )
    )
  }
}

tasks.test {
  useJUnitPlatform()
  maxHeapSize = "1g"
}
