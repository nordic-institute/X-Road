plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
  id("maven-publish")
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      project.extensions.getByType<com.github.jengelman.gradle.plugins.shadow.ShadowExtension>()
        .component(this)
      groupId = "org.niis.xroad"
      artifactId = "configuration-client"
      version = buildString {
        append(project.findProperty("xroadVersion") ?: "")
        if (project.findProperty("xroadBuildType") != "RELEASE") {
          append("-SNAPSHOT")
        }
      }
    }
  }
  repositories {
    maven {
      url = uri(project.findProperty("xroadPublishUrl") ?: "")
      credentials {
        username = project.findProperty("xroadPublishUser")?.toString()
        password = project.findProperty("xroadPublishApiKey")?.toString()
      }
      authentication {
        create<BasicAuthentication>("basic")
      }
    }
  }
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(project(":service:configuration-client:configuration-client-core"))

  implementation("org.springframework:spring-context")

  testImplementation(project(":common:common-test"))
}

tasks.jar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.confclient.application.ConfClientDaemonMain")
  }
}

tasks.shadowJar {
  exclude("**/module-info.class")
  archiveClassifier.set("")
  archiveBaseName.set("configuration-client")
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
