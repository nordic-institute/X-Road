plugins {
  java
  id("com.google.cloud.tools.jib")
}

jib {
  setAllowInsecureRegistries(true)
  from {
    val baseImageTag = project.findProperty("baseImageTag") ?: "latest"
    image = "${project.property("xroadImageRegistry")}/ss-baseline-runtime:${baseImageTag}"
    platforms {
      platform {
        architecture = "arm64"
        os = "linux"
      }
      platform {
        architecture = "amd64"
        os = "linux"
      }
    }
  }

  to {
    tags = setOf("latest")
  }

  container {
    appRoot = "/opt/app"
    creationTime.set("USE_CURRENT_TIMESTAMP")
    user = "xroad"
  }
}

val buildImages: String = project.findProperty("buildImages")?.toString() ?: "false"
if (!buildImages.toBoolean()) {
  tasks.withType<com.google.cloud.tools.jib.gradle.JibTask>().configureEach {
    enabled = false
  }
} else {
  tasks.withType<com.google.cloud.tools.jib.gradle.JibTask>().configureEach {
    doFirst {
      System.setProperty("jib.console", "plain")
    }
  }
}

tasks.register<Copy>("prepareLicenseFiles") {
  into(layout.buildDirectory.dir("jib-extra/license"))
  from(rootProject.file("LICENSE.txt"))
  from(rootProject.file("3RD-PARTY-NOTICES.txt"))
}
