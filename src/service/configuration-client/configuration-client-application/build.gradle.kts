plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
  id("maven-publish")
}


publishing {
  publications {
    create<MavenPublication>("quarkus") {
      from(components["java"])

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
      val publishUrl = project.findProperty("xroadPublishUrl")?.toString()
      if (!publishUrl.isNullOrBlank()) {
        url = uri(publishUrl)
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
}

dependencies {
  implementation(project(":lib:rpc-quarkus"))
  implementation(project(":common:common-core"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":service:configuration-client:configuration-client-core"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)
  implementation(libs.quarkus.quartz)

  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}
