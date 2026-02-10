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
  implementation(project(":service:signer:signer-client-spring"))
  implementation(project(":lib:serverconf-spring"))
  implementation(project(":lib:rpc-spring"))
  implementation(project(":common:common-admin-api"))
  implementation(project(":common:common-management-request"))
  implementation(project(":common:common-api-throttling"))
  implementation(project(":common:common-pgp"))
  implementation(project(":lib:properties-spring"))
  implementation(project(":security-server:openapi-model"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":service:op-monitor:op-monitor-client"))
  implementation(project(":service:backup-manager:backup-manager-rpc-client"))
  implementation(project(":service:configuration-client:configuration-client-rpc-client"))
  implementation(project(":service:monitor:monitor-rpc-client"))
  implementation(project(":service:proxy:proxy-rpc-client"))

  implementation(project(":security-server:admin-service:message-log-archiver")) {
    exclude(group = "xerces", module = "xercesImpl") // inclusion replaces xerces as default wsdl parser & introduces XXE vulnerability
  }

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("io.micrometer:micrometer-tracing-bridge-brave")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation(libs.acme4j)
  implementation(libs.libpam4j)
  implementation(libs.apache.commonsCompress)
  implementation(libs.wsdl4j)
  implementation(libs.bucket4j.core)
  implementation(libs.swagger.parserV3)
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
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation(libs.hsqldb)
  testImplementation(libs.jsonUnit.assertj)
  testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
  testImplementation(testFixtures(project(":common:common-api-throttling")))
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

tasks.register<Copy>("copyDeps") {
  into(layout.buildDirectory.dir("unpacked-libs"))
  from(configurations.runtimeClasspath.get().find { it.name.startsWith("postgresql") })
}

tasks.assemble {
  dependsOn(tasks.named("copyDeps"))
}

tasks.test {
  useJUnitPlatform()
  maxHeapSize = "1g"
}
