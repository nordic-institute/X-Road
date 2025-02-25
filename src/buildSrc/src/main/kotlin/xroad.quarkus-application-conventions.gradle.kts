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
      put("quarkus.package.jar.type", "fast-jar")
      put("quarkus.container-image.build", "true")
      put("quarkus.container-image.registry", "${project.property("xroadImageRegistry")}")
      put("quarkus.container-image.insecure", "true")
      put("quarkus.container-image.push", "true")
      put("quarkus.container-image.builder", "jib")
      put("quarkus.jib.working-directory", "/opt/app")

      put("quarkus.jib.base-jvm-image", "${project.property("xroadImageRegistry")}/ss-baseline-runtime:latest")
      put("quarkus.jib.platforms", "linux/amd64,linux/arm64/v8")
      put("quarkus.jib.user", "xroad")

      val jvmArgs = mutableListOf("-Dquarkus.profile=containerized")

      if (buildEnv == "dev") {
        // Add debug parameters - each as a separate list item
        jvmArgs.add("-Xdebug")
        jvmArgs.add("-agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n")

        // Add JMX parameters - each as a separate list item
        jvmArgs.add("-Dcom.sun.management.jmxremote=true")
        jvmArgs.add("-Dcom.sun.management.jmxremote.local.only=false")
        jvmArgs.add("-Dcom.sun.management.jmxremote.authenticate=false")
        jvmArgs.add("-Dcom.sun.management.jmxremote.ssl=false")
        jvmArgs.add("-Djava.rmi.server.hostname=localhost")
        jvmArgs.add("-Dcom.sun.management.jmxremote.port=9990")
        jvmArgs.add("-Dcom.sun.management.jmxremote.rmi.port=9990")
      }

// Set the JVM arguments
      jvmArgs.forEachIndexed { index, arg ->
        put("quarkus.jib.jvm-additional-arguments[$index]", arg)
      }
    }
  )
}

tasks {
  named("compileJava") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
  }
  test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  }
  jar {
    enabled = false
  }
}
