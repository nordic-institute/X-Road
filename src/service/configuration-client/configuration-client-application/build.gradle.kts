plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.quarkus)
  id("maven-publish")
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "native"

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      // Common properties
      put("quarkus.package.output-directory", "libs")
      put("quarkus.package.output-name", "configuration-client-1.0")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }
        "containerized" -> {
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.group", "niis")
          put("quarkus.container-image.name", "xroad-configuration-client")
          put("quarkus.container-image.tag", "latest")
        }
        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
    }
  )
}

tasks {
  named<JavaCompile>("compileJava") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
  }
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
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-core"))
  implementation(project(":service:configuration-client:configuration-client-core"))

  implementation(libs.bundles.quarkus.core)
  implementation(libs.quarkus.quartz)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.junit.system.exit)
  testImplementation(libs.quarkus.junit5)
}

tasks.jar {
  enabled = false
}

tasks.test {
  jvmArgumentProviders.add(CommandLineArgumentProvider {
    listOf(
      "-javaagent:${
        configurations.testRuntimeClasspath.get().files.find {
          it.name.contains("junit5-system-exit")
        }
      }")
  })
}
