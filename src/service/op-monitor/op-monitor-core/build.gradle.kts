plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

val xjc by configurations.creating

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
    resources.srcDirs("../../../common/common-domain/src/main/resources")
  }
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(libs.jakarta.validationApi)
  implementation(libs.bundles.metrics)
  implementation(libs.mapstruct)
  implementation(libs.quarkus.scheduler)

  implementation(project(":common:common-core"))
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-db"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":lib:properties-core"))
  implementation(project(":service:op-monitor:op-monitor-api"))

  testImplementation(libs.hsqldb)
  testImplementation(project(":common:common-test"))
  testImplementation(libs.commons.cli)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.junit.platform.launcher)
  testImplementation(libs.logback.classic)
  testImplementation(testFixtures(project(":lib:rpc-core")))
  testImplementation(testFixtures(project(":lib:properties-core")))

  xjc(libs.bundles.jaxb)
}

val createDirs by tasks.registering {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.register("xjc") {
  description = "Generate Java classes from XSD schema"
  group = "build"

  inputs.files(fileTree("src/main/resources").include("*.xsd"))
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to xjc.asPath
      )

      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.opmonitordaemon.message",
        "schema" to "${layout.buildDirectory.get().asFile}/resources/main/op-monitoring.xsd",
        "binding" to "${layout.buildDirectory.get().asFile}/resources/main/identifiers-bindings.xml"
      )
    }
  }
}

tasks.named("xjc") {
  dependsOn(createDirs)
  dependsOn(tasks.processResources)
  mustRunAfter(tasks.processResources)
}

tasks.compileJava {
  dependsOn(tasks.named("xjc"))
  dependsOn(tasks.processResources)
}

val identityScript = rootProject.file(
  "../deployment/native-packages/src/xroad/common/op-monitor/usr/share/xroad/scripts/generate_opmonitor_rpc_identity.sh"
)

tasks.test {
  inputs.file(identityScript).withPropertyName("identityScript")
}

val rootScriptTestDir = layout.buildDirectory.dir("root-script-test")

val stageRootScriptTest by tasks.registering(Sync::class) {
  description = "Manual root check: stages the identity tool tests for the rootScriptTest container run."
  group = "verification"

  val runtimeClasspath = sourceSets.test.get().runtimeClasspath
  from(runtimeClasspath.filter { it.isDirectory }) {
    into("classes")
  }
  from(runtimeClasspath.filter { it.isFile }) {
    into("lib")
  }
  into(rootScriptTestDir)
}

tasks.register<Exec>("rootScriptTest") {
  description = "Manual root check, not part of check or CI: runs the root-dependent op-monitor identity tool tests as root " +
    "in an Ubuntu container. Root ownership and permissions are verified with this task, not on every CI run."
  group = "verification"

  dependsOn(stageRootScriptTest)
  inputs.file(identityScript).withPropertyName("identityScript")
  inputs.dir(rootScriptTestDir).withPropertyName("stagedTests")

  val coreDir = rootProject.file("..").canonicalFile
  val moduleDir = "/work/core/" + coreDir.toPath().relativize(projectDir.toPath())
  val install = "export DEBIAN_FRONTEND=noninteractive && apt-get update -qq && apt-get install -y -qq openjdk-25-jre-headless openssl >/dev/null"
  val run = "java -cp 'build/root-script-test/classes:build/root-script-test/lib/*' " +
    "org.niis.xroad.opmonitor.core.identity.RootScriptTestLauncher root"
  commandLine(
    "docker", "run", "--rm",
    "--volume", "$coreDir:/work/core:ro",
    "--workdir", moduleDir,
    "ubuntu:26.04",
    "bash", "-ec", "$install && useradd --system xroad && $run"
  )
}
