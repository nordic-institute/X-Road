plugins {
  id("maven-publish")
}

// The release/snapshot rule decides which Artifactory repository an artifact is accepted by, and a
// module that drifts from it publishes a snapshot version into the release repository or the reverse.
// It is therefore defined once here rather than per module.
fun resolvePublishVersion(): String = buildString {
  append(project.findProperty("xroadVersion") ?: "")
  if (project.findProperty("xroadBuildType") != "RELEASE") {
    append("-SNAPSHOT")
  }
}

publishing {
  publications.withType<MavenPublication>().configureEach {
    groupId = "org.niis.xroad"
    version = resolvePublishVersion()
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
