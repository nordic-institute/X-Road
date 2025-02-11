plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":common:common-jetty"))
  implementation(project(":service:op-monitor:op-monitor-api"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:serverconf-impl"))
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

  testImplementation(project(":common:common-test"))
  testImplementation(project(path = ":service:proxy:proxy-application", configuration = "testArtifacts"))

  testImplementation(libs.wiremock.standalone)
  testImplementation(libs.wsdl4j)
  testImplementation(libs.apache.httpmime)
  testImplementation(libs.xmlunit.core)
  testImplementation(libs.xmlunit.matchers)
  testImplementation(libs.hsqldb)
  testImplementation(libs.mockito.core)
  testImplementation(libs.jsonUnit.assertj)

  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
}

val runMetaserviceTest by tasks.registering(JavaExec::class) {
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
    "-Dxroad.proxy.client-httpclient-so-linger=-1",
    "-Dxroad.proxy.serverServiceHandlers=org.niis.xroad.proxy.core.serverproxy.MetadataServiceHandlerImpl",
    "-Dxroad.proxy.clientHandlers=org.niis.xroad.proxy.core..clientproxy.MetadataHandler",
    "-Dxroad.common.grpc-internal-tls-enabled=false",
    "-Dtest.queries.dir=src/test/queries"
  )

  mainClass.set("org.niis.xroad.proxy.application.testsuite.ProxyTestSuite")
  classpath(sourceSets.test.get().runtimeClasspath)
}

project.extensions.getByType<JacocoPluginExtension>().applyTo(tasks.named<JavaExec>("runMetaserviceTest").get())
