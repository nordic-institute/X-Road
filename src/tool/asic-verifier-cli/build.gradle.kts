plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
  id("maven-publish")
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))
  implementation(libs.logback.classic)
  testImplementation(project(":common:common-test"))
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.asic.verifier.cli.AsicVerifierMain")
  }
  archiveBaseName.set("asicverifier")
  archiveClassifier.set("")
  archiveVersion.set("")

}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      from(components["shadow"])

      groupId = "org.niis.xroad"
      artifactId = "asicverifier"
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
