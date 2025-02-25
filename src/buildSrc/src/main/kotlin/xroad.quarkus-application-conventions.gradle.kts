plugins {
  id("xroad.jib-conventions")
  id("io.quarkus")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  implementation(platform(libs.findLibrary("quarkus-bom").get()))
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "containerized"
val buildEnv: String = project.findProperty("buildEnv")?.toString() ?: "dev" //TODO default to prod later

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.container-image.build", "false")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "fast-jar")
        }

        "containerized" -> {
          put("quarkus.package.jar.type", "fast-jar")
        }

        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
    }
  )
}

jib {
  container {
    creationTime.set("USE_CURRENT_TIMESTAMP")
    jvmFlags = buildList {
      add("-Djava.util.logging.manager=org.jboss.logmanager.LogManager")
      add("-Dquarkus.profile=containerized")

      // Add debug and JMX flags only in dev environment
      if (buildEnv == "dev") {
        add("-Xdebug")
        add("-agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n")
        add("-Dcom.sun.management.jmxremote=true")
        add("-Dcom.sun.management.jmxremote.local.only=false")
        add("-Dcom.sun.management.jmxremote.authenticate=false")
        add("-Dcom.sun.management.jmxremote.ssl=false")
        add("-Djava.rmi.server.hostname=localhost")
        add("-Dcom.sun.management.jmxremote.port=9990")
        add("-Dcom.sun.management.jmxremote.rmi.port=9990")
      }
    }
  }

  pluginExtensions {
    pluginExtension {
      implementation = "com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension"
      properties = mapOf("packageType" to "fast-jar")
    }
  }

  extraDirectories {
    paths {
      path {
        setFrom(project.file("../../../packages/docker/entrypoint/").toPath())
        into = "/app"
      }
    }
  }
}

tasks {
  named("compileJava") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
  }
  test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  }
  named("jib") {
    dependsOn("quarkusBuild")
  }
  assemble {
    dependsOn(tasks.named("jib"))
  }
  jar {
    enabled = false
  }
}
