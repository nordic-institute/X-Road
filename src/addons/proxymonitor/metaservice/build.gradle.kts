plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

sourceSets {
  main {
    java.srcDirs("src/main/java", schemaTargetDir)
    resources.srcDirs("src/main/resources", "../../../common/common-domain/src/main/resources")
  }
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-domain"))
  implementation(project(":common:common-jetty"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation(project(":service:monitor:monitor-api"))
  implementation(project(":addons:proxymonitor-common"))

  implementation(libs.guava)

  testImplementation(project(":common:common-test"))
  testImplementation(project(path = "::service:proxy:proxy-application", configuration = "testArtifacts"))
  testImplementation(libs.hamcrest)

  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
}

tasks.register("createDirs") {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  archiveClassifier.set("")
  exclude("**/module-info.class")
  dependencies {
    include(project(":addons:proxymonitor-common"))
    include(project(":service:monitor:monitor-api"))
  }
  mergeServiceFiles()
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

tasks.compileJava {
  dependsOn(tasks.processResources)
}

tasks.register<JavaExec>("runProxymonitorMetaserviceTest") {
  group = "verification"
  if (System.getProperty("DEBUG", "false") == "true") {
    jvmArgs(
      "-Xdebug",
      "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
    )
  }

  jvmArgs(
    "-Dxroad.proxy.ocspCachePath=build/ocsp-cache",
    "-Dxroad.tempFiles.path=build/attach-tmp",
    "-Dxroad.proxy.configurationFile=../../systemtest/conf/local_test/serverconf_producer.xml",
    "-Dxroad.proxy.jetty-serverproxy-configuration-file=src/test/resources/serverproxy.xml",
    "-Dxroad.proxy.jetty-clientproxy-configuration-file=src/test/resources/clientproxy.xml",
    "-Dlogback.configurationFile=src/test/resources/logback-metaservicetest.xml",
    "-Dxroad.proxy.jetty-ocsp-responder-configuration-file=src/test/resources/ocsp-responder.xml",
    "-Dxroad.proxy.client-connector-so-linger=-1",
    "-Dxroad.proxy.client-httpclient-so-linger=-1",
    "-Dxroad.proxy.server-connector-so-linger=-1",
    "-Dxroad.proxy.serverServiceHandlers=org.niis.xroad.proxy.core.serverproxy.ProxyMonitorServiceHandlerImpl",
    "-Dxroad.common.grpc-internal-tls-enabled=false",
    "-Dtest.queries.dir=src/test/queries"
  )

  mainClass.set("org.niis.xroad.proxy.application.testsuite.ProxyTestSuite")
  classpath(sourceSets.test.get().runtimeClasspath)
}

project.extensions.getByType<JacocoPluginExtension>().applyTo(tasks.named<JavaExec>("runProxymonitorMetaserviceTest").get())
