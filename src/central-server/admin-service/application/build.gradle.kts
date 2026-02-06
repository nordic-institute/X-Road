plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.springBoot)
}

base {
  archivesName.set("centralserver-admin-service")
}

configurations {
  create("dist") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
  create("liquibaseLibs") {
    apply(plugin = "base")
  }
}

dependencies {
  add("dist", project(path = ":central-server:admin-service:ui", configuration = "dist"))

  implementation(project(":central-server:admin-service:core"))
  implementation(project(":central-server:admin-service:core-api"))
  implementation(project(":central-server:admin-service:infra-api-rest"))
  implementation(project(":central-server:admin-service:infra-jpa"))
  implementation(project(":central-server:admin-service:globalconf-generator"))
  implementation(project(":central-server:openapi-model"))
  implementation(project(":common:common-db"))
  implementation(project(":lib:properties-spring"))
  implementation(libs.logback.classic)

  testImplementation(project(":common:common-test"))
  testImplementation(testFixtures(project(":common:common-api-throttling")))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.liquibase:liquibase-core")
}

tasks.bootRun {
  jvmArgs = listOf("-Dspring.output.ansi.enabled=ALWAYS")
  if (project.hasProperty("args")) {
    args = project.property("args").toString().split(",")
  }
}


tasks.register<ProcessResources>("copyUi") {
  dependsOn(configurations["dist"])
  from(configurations["dist"])
  into(layout.buildDirectory.dir("admin-service/ui/public"))
}

tasks.jar {
  enabled = false
}

tasks.bootJar {
  enabled = true

  if (!project.hasProperty("skip-frontend-build")) {
    dependsOn(tasks.named("copyUi"))
    classpath(layout.buildDirectory.dir("admin-service/ui"))
  } else {
    println("Warning: Excluding frontend from boot jar")
  }

  manifest {
    attributes(
      mapOf(
        "Implementation-Title" to "X-Road Central Server Admin Service",
        "Implementation-Version" to "${project.property("xroadVersion")}-${project.property("xroadBuildType")}"
      )
    )
  }
}

tasks.register<Copy>("moveLiquibaseLibs") {
  doFirst {
    mkdir(layout.buildDirectory.dir("libs"))
  }

  from(configurations["liquibaseLibs"])
  into(layout.buildDirectory.dir("libs"))
}

tasks.build {
  dependsOn(tasks.named("moveLiquibaseLibs"))
}
