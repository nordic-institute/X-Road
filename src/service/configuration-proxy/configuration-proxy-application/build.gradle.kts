plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-jetty"))
  implementation(project(":service:signer:signer-client"))

  implementation(libs.commons.cli)

  testImplementation(project(":common:common-test"))
  testImplementation(libs.assertj.core)
}

val mainClassName = "org.niis.xroad.confproxy.ConfProxyMain"

tasks.jar {
  manifest {
    attributes("Main-Class" to mainClassName)
  }
}

tasks.shadowJar {
  archiveBaseName.set("configuration-proxy")
  archiveClassifier.set("")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.jar {
  enabled = false
}

tasks.register<JavaExec>("runConfigurationProxyMain") {
  description = "Runs the configuration proxy"
  group = "Execution"

  jvmArgs(
    "-Dxroad.conf.path=src/test/resources/",
    "-Dxroad.common.configuration-path=build/",
    "-Dxroad.configuration-proxy.configuration-path=src/test/resources/conf-proxy-conf"
  )

  mainClass.set("org.niis.xroad.confproxy.ConfProxyMain")
  classpath = sourceSets.test.get().runtimeClasspath

  args("PROXY1")
}

tasks.register<JavaExec>("runConfigurationProxyUtilTest") {
  description = "Runs the configuration proxy utility test"
  group = "Execution"
  jvmArgs(
    "-Dxroad.conf.path=src/test/resources/",
    "-Dxroad.configuration-proxy.configuration-path=src/test/resources/conf-proxy-conf"
  )

  mainClass.set("org.niis.xroad.confproxy.commandline.ConfProxyUtilMain")
  classpath = sourceSets.test.get().runtimeClasspath

  args(
    "org.niis.xroad.confproxy.commandline.ConfProxyUtilGenerateAnchor",
    "-p", "PROXY1", "-f", "test.xml"
  )
  // Commented out alternative args:
  // args("org.niis.xroad.confproxy.commandline.ConfProxyUtilAddSigningKey", "-p", "PROXY1", "-t", "0")
  // args("org.niis.xroad.confproxy.commandline.ConfProxyUtilDelSigningKey", "-p", "PROXY1", "-k", "B8F553EC0944EB8022B29166D5C13E6298FB6616")
  // args("org.niis.xroad.confproxy.commandline.ConfProxyUtilViewConf", "-a")
  // args("org.niis.xroad.confproxy.commandline.ConfProxyUtilCreateInstance", "-p", "PROXY2")
}
