import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":tool:api-test-core"))
  intTestImplementation(project(":lib:asic-core"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":lib:globalconf-impl"))
  intTestImplementation(project(":lib:vault-core"))
  intTestImplementation(project(":service:op-monitor:op-monitor-core")) {
    exclude(group = "org.jboss.slf4j", module = "slf4j-jboss-logmanager")
  }
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "CS_IMG" to "central-server-dev",
    "POSTGRES_DEV_IMG" to "postgres-dev",
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "DB_INIT_IMG" to "ss-db-init",
    "CONFIGURATION_CLIENT_IMG" to "ss-configuration-client",
    "MONITOR_IMG" to "ss-monitor",
    "SIGNER_IMG" to "ss-signer",
    "SOFTTOKEN_SIGNER_IMG" to "ss-softtoken-signer",
    "PROXY_IMG" to "ss-proxy",
    "PROXY_UI_IMG" to "ss-proxy-ui-api",
    "AUXILIARY_SERVICE_IMG" to "ss-auxiliary-service",
    "OP_MONITOR_IMG" to "ss-op-monitor",
    "CA_IMG" to "testca-dev",
    "MESSAGE_LOG_ARCHIVER_IMG" to "ss-message-log-archiver",
    "DS_CONTROL_PLANE_IMG" to "ds-control-plane",
    "DS_IDENTITY_HUB_IMG" to "ds-identity-hub"
  )
}

intTestShadowJar {
  archiveBaseName("e2e-test")
  mainClass("org.niis.xroad.e2e.ConsoleE2ETestRunner")
}

val copyComposeFiles by tasks.registering(Copy::class) {
  description = "Copies compose files and related resources to build directory for e2e tests"
  group = "verification"

  from("../../../development/docker/security-server/compose.yaml") {
    rename { "compose.main.yaml" }
  }
  from("../../../development/hurl") {
    into("hurl")
  }
  from("../../../development/docker/security-server/signer-with-hsm") {
    into("signer-with-hsm")
  }
  into("build/resources/intTest")
}

val e2eEnvMode = providers.gradleProperty("e2e.env-mode").getOrElse("compose")

tasks.register<Test>("e2eTest") {
  // Only the harness-managed compose stack needs the generated env and copied compose files. Pre-provisioned
  // targets (lxd, and any future deployment mode) attach to an environment that already exists.
  if (e2eEnvMode == "compose") {
    dependsOn(provider { tasks.named("generateIntTestEnv") })
    dependsOn(provider { tasks.named("copyComposeFiles") })
  }
  useJUnitPlatform()

  description = "Runs the e2e test suite in scenario order. The target environment is selected by " +
      "-Pe2e.env-mode (default 'compose': harness-boots the shared aux/ss0/ss1 stack via the " +
      "LauncherSessionListener SPI; 'lxd': attaches to a pre-provisioned LXD environment). " +
      "Pass --tests <pattern> to run a single class/method directly (IDE-friendly)."
  group = "verification"

  testClassesDirs = sourceSets["intTest"].output.classesDirs
  classpath = sourceSets["intTest"].runtimeClasspath

  // Select only the E2eSuite by default so the topology boots exactly once per run: the suite itself
  // discovers and runs every scenario class. An unfiltered scan of testClassesDirs would otherwise let
  // the Jupiter engine run each scenario class a second time alongside the suite engine's own run of it.
  val suiteClass = "E2eSuite"
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

  systemProperty("test-framework.env-mode", e2eEnvMode)

  jvmArgs("-XX:MaxMetaspaceSize=200m")

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
  dependsOn(provider { tasks.named("copyComposeFiles") })
}

tasks.named<ShadowJar>("shadowJar") {
  dependsOn(provider { tasks.named("copyComposeFiles") })
}

