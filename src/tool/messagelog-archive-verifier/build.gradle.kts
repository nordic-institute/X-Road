plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
  id("maven-publish")
}

dependencies {
  implementation(project(":common:common-core"))
}

val mainClassName = "org.niis.xroad.cli.ArchiveHashChainVerifier"

tasks {
  jar {
    manifest {
      attributes["Main-Class"] = mainClassName
    }
    enabled = false
  }

  shadowJar {
    archiveVersion.set("")
    archiveClassifier.set("")
    from(rootProject.file("LICENSE.txt"))
  }
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      from(components["shadow"])

      groupId = "org.niis.xroad"
      artifactId = "messagelog-archive-verifier"
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
