plugins {
  java
  id("com.google.cloud.tools.jib")
}

jib {
  setAllowInsecureRegistries(true)
  from {
    image = "${project.property("xroadImageRegistry")}/ss-baseline-runtime:latest"
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
    appRoot = "/app"
    creationTime.set("USE_CURRENT_TIMESTAMP")
    user = "xroad"
  }
}
