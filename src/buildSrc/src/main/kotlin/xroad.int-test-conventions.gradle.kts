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
  "intTestImplementation"(platform(libs.findLibrary("testcontainers-core").get()))
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
      // Let Shadow bundle all intTest dependencies (jars + project class dirs)
      configurations = listOf(project.configurations["intTestRuntimeClasspath"])

      mergeServiceFiles()
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

/**
 * Extension for configuring the phased api-test intTest task shared by CS and SS api-test modules.
 * Set phasedSuiteClass to opt in; leave blank to register the intTest task manually in the build script.
 */
abstract class IntTestPhasedSuiteExtension {
  var phasedSuiteClass: String = ""
  var productName: String = ""
}

val intTestPhasedSuite = project.extensions.create<IntTestPhasedSuiteExtension>("intTestPhasedSuite")

afterEvaluate {
  if (intTestPhasedSuite.phasedSuiteClass.isNotBlank()) {
    tasks.register<Test>("intTest") {
      dependsOn(provider { tasks.named("generateIntTestEnv") })
      if (tasks.names.contains("copyMainComposeFile")) {
        dependsOn(tasks.named("copyMainComposeFile"))
      }

      description = "Runs the full phased ${intTestPhasedSuite.productName} API test suite " +
          "(non-destructive parallel first, destructive serial last). " +
          "Pass --tests <pattern> to run a single class/method directly (IDE-friendly); " +
          "the stack still boots via @ExtendWith."
      group = "verification"

      testClassesDirs = sourceSets["intTest"].output.classesDirs
      classpath = sourceSets["intTest"].runtimeClasspath

      useJUnitPlatform()

      val suiteClass = intTestPhasedSuite.phasedSuiteClass
      val singleTestFromCli = gradle.startParameter.taskRequests.any { request ->
        request.args.any { it == "--tests" || it.startsWith("--tests=") }
      }
      include(if (singleTestFromCli) "**/*Test.class" else "**/$suiteClass.class")
      doFirst {
        val testFilter = filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
        val patterns = testFilter.commandLineIncludePatterns + testFilter.includePatterns
        val targetsSuite = patterns.any { it.substringBefore('*').trimEnd('.').substringAfterLast('.') == suiteClass }
        when {
          // Naming the suite via --tests (e.g. the IDE gutter run on the suite class) must behave like the
          // unfiltered run: select the suite class and drop the test-name filter. Otherwise Gradle matches the
          // filter against the suite's nested scenario classes by their own names and strips every one of them.
          targetsSuite -> {
            setIncludes(setOf("**/$suiteClass.class"))
            testFilter.setCommandLineIncludePatterns(emptyList())
            testFilter.setIncludePatterns()
          }
          patterns.isNotEmpty() -> setIncludes(setOf("**/*Test.class"))
        }
      }

      maxParallelForks = 1
      setForkEvery(0)

      systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
      project.findProperty("apiTestFailFastThreshold")?.let {
        systemProperty("test-framework.fail-fast.threshold", it.toString())
      }

      maxHeapSize = "256m"

      testLogging {
        showStackTraces = true
        showExceptions = true
        showCauses = true
        showStandardStreams = true
      }
    }

    tasks.named<Checkstyle>("checkstyleIntTest") {
      dependsOn(provider { tasks.named("generateIntTestEnv") })
      if (tasks.names.contains("copyMainComposeFile")) {
        dependsOn(tasks.named("copyMainComposeFile"))
      }
    }
  }
}

// Make helper functions available to build scripts (for backwards compatibility if needed)
extra["resolveIntTestImageTag"] = ::resolveIntTestImageTag
extra["resolveIntTestImageRegistry"] = ::resolveIntTestImageRegistry
