import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.time.Instant

plugins {
  java
  id("com.gradleup.shadow")
}

sourceSets.create("intTest") {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}

configurations {
  val intTestImplementation by getting {
    extendsFrom(configurations.implementation.get())
  }
  val intTestRuntimeOnly by getting {
    extendsFrom(configurations.runtimeOnly.get())
  }
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  "intTestCompileOnly"(libs.findLibrary("lombok").get())
  "intTestAnnotationProcessor"(libs.findLibrary("lombok").get())
}

tasks.named<Checkstyle>("checkstyleIntTest") {
  source = fileTree("src/intTest/java")
  configFile = file("${project.rootDir}/config/checkstyle/checkstyle-test.xml")
}

fun resolveIntTestImageTag(): String {
  val overrideTag = project.findProperty("xroadImageTag")?.toString()
  if (overrideTag != null) {
    return overrideTag
  }

  val version = project.findProperty("xroadVersion")
  val buildType = project.findProperty("xroadBuildType")

  return if (buildType == "RELEASE") {
    version.toString()
  } else {
    "$version-$buildType"
  }
}

fun resolveIntTestImageRegistry(): String {
  return project.findProperty("xroadImageRegistry")?.toString()!!
}

/**
 * Extension for configuring Docker Compose .env file for integration tests
 */
abstract class IntTestComposeEnvExtension {
  val images = mutableMapOf<String, String>()
  val additionalVars = mutableMapOf<String, String>()

  /**
   * Add a Docker image to the .env file
   * @param envVar Environment variable name (e.g., "OP_MONITOR_IMG")
   * @param imageName Docker image name (e.g., "ss-op-monitor")
   */
  fun image(envVar: String, imageName: String) {
    images[envVar] = imageName
  }

  /**
   * Convenience method to add multiple images
   * @param imageNames Pairs of environment variable name to Docker image name
   */
  fun images(vararg imageNames: Pair<String, String>) {
    imageNames.forEach { (envVar, imageName) ->
      image(envVar, imageName)
    }
  }

  /**
   * Add a custom environment variable (non-image)
   * @param envVar Environment variable name
   * @param value Value for the environment variable
   */
  fun env(envVar: String, value: String) {
    additionalVars[envVar] = value
  }

  /**
   * Convenience method to add multiple environment variables
   */
  fun envs(vararg envVars: Pair<String, String>) {
    envVars.forEach { (envVar, value) ->
      env(envVar, value)
    }
  }
}

// Create extension
val intTestComposeEnv = project.extensions.create<IntTestComposeEnvExtension>("intTestComposeEnv")

afterEvaluate {
  if (intTestComposeEnv.images.isNotEmpty() && !tasks.names.contains("generateIntTestEnv")) {
    tasks.register("generateIntTestEnv") {
      description = "Generates .env file for integration tests with resolved image tags"
      group = "verification"

      dependsOn(tasks.named("processIntTestResources"))

      val outputEnvFile = file("build/resources/intTest/.env")

      // Inputs: track what affects .env generation
      inputs.property("imageTag", provider { resolveIntTestImageTag() })
      inputs.property("imageRegistry", provider { resolveIntTestImageRegistry() })
      inputs.property("images", provider { intTestComposeEnv.images.toString() })
      inputs.property("additionalVars", provider { intTestComposeEnv.additionalVars.toString() })

      outputs.file(outputEnvFile)

      doLast {
        val imageTag = resolveIntTestImageTag()
        val imageRegistry = resolveIntTestImageRegistry()

        logger.lifecycle("Generating .env file for integration tests:")
        logger.lifecycle("  Registry: $imageRegistry")
        logger.lifecycle("  Tag: $imageTag")

        val envContent = buildString {
          appendLine("# Auto-generated .env file for integration tests")
          appendLine("# Generated at: ${Instant.now()}")
          appendLine("# Registry: $imageRegistry")
          appendLine("# Tag: $imageTag")
          appendLine()

          // Additional environment variables
          if (intTestComposeEnv.additionalVars.isNotEmpty()) {
            intTestComposeEnv.additionalVars.forEach { (envVar, value) ->
              appendLine("$envVar=$value")
            }
            appendLine()
          }

          // Docker images
          intTestComposeEnv.images.forEach { (envVar, imageName) ->
            appendLine("$envVar=$imageRegistry/$imageName:$imageTag")
          }
        }

        outputEnvFile.writeText(envContent)
        logger.lifecycle("Generated: ${outputEnvFile.absolutePath}")
      }
    }
  }
}

/**
 * Extension for configuring int-test shadow jar
 */
abstract class IntTestShadowJarExtension {
  val archiveBaseName = mutableListOf<String>()
  val mainClass = mutableListOf<String>()

  /**
   * Set the archive base name for the shadow jar
   */
  fun archiveBaseName(name: String) {
    archiveBaseName.clear()
    archiveBaseName.add(name)
  }

  /**
   * Set the main class for the shadow jar manifest
   */
  fun mainClass(className: String) {
    mainClass.clear()
    mainClass.add(className)
  }
}

// Create shadowJar extension
val intTestShadowJar = project.extensions.create<IntTestShadowJarExtension>("intTestShadowJar")

// Disable standard jar task
tasks.jar {
  enabled = false
}

// Connect build to shadowJar
tasks.build {
  dependsOn(tasks.named("shadowJar"))
}

// Configure shadowJar with common settings
afterEvaluate {
  if (intTestShadowJar.archiveBaseName.isNotEmpty() && intTestShadowJar.mainClass.isNotEmpty()) {
    tasks.named<ShadowJar>("shadowJar") {
      archiveBaseName.set(intTestShadowJar.archiveBaseName.first())
      archiveClassifier.set("")
      isZip64 = true

      from(sourceSets["intTest"].output.classesDirs)

      from("${layout.buildDirectory.get().asFile}/resources/intTest") {
        into("")
      }
      from("${layout.buildDirectory.get().asFile}/resources/intTest/.env") {
        into("")
      }
      from(sourceSets["intTest"].runtimeClasspath.filter { it.name.endsWith(".jar") })

      mergeServiceFiles()
      exclude("**/module-info.class")

      manifest {
        attributes(
          "Main-Class" to intTestShadowJar.mainClass.first()
        )
      }

      // Add generateIntTestEnv dependency if the task exists
      if (tasks.names.contains("generateIntTestEnv")) {
        dependsOn(provider { tasks.named("generateIntTestEnv") })
      }
      dependsOn(tasks.named("intTestClasses"))
      dependsOn(tasks.named("processIntTestResources"))
    }
  }
}

// Make helper functions available to build scripts (for backwards compatibility if needed)
extra["resolveIntTestImageTag"] = ::resolveIntTestImageTag
extra["resolveIntTestImageRegistry"] = ::resolveIntTestImageRegistry
