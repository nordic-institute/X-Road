plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(libs.logback.classic)

  testImplementation(libs.hsqldb)
  testImplementation(libs.restAssured)
  testImplementation(libs.apache.httpasyncclient)
  testImplementation(project(":common:common-domain"))
  testImplementation(project(":common:common-jetty"))
  testImplementation(project(":common:common-message"))
  testImplementation(project(":common:common-test"))

  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))
}

tasks.jar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.proxy.application.ProxyMain")
  }
  archiveClassifier.set("plain")
  archiveBaseName.set("proxy")
}

tasks.shadowJar {
  archiveClassifier.set("")
  archiveBaseName.set("proxy")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

val testJar by tasks.registering(Jar::class) {
  archiveClassifier.set("test")
  from(sourceSets.test.get().output)
}

configurations {
  create("testArtifacts") {
    extendsFrom(configurations.testRuntimeOnly.get())
  }
}

artifacts {
  add("testArtifacts", testJar)
}

tasks.assemble {
  finalizedBy(tasks.shadowJar)
}

val runProxyTest by tasks.registering(JavaExec::class) {
  group = "verification"
  shouldRunAfter(tasks.test)
  jvmArgs(
    "-Xmx2g",
    "-Dxroad.proxy.ocspCachePath=build/ocsp-cache",
    "-Dxroad.tempFiles.path=build/attach-tmp",
    "-Dxroad.proxy.jetty-serverproxy-configuration-file=src/test/serverproxy.xml",
    "-Dxroad.proxy.jetty-ocsp-responder-configuration-file=src/test/ocsp-responder.xml",
    "-Dxroad.proxy.jetty-clientproxy-configuration-file=src/test/clientproxy.xml",
    "-Dxroad.proxy.client-connector-so-linger=-1",
    "-Dxroad.proxy.client-httpclient-so-linger=-1",
    "-Dxroad.proxy.server-connector-so-linger=-1",
    "-Dlogback.configurationFile=src/test/logback-proxytest.xml",
    "-Dxroad.common.grpc-internal-tls-enabled=false"
    // "-Djava.security.properties==src/main/resources/java.security"
  )

  mainClass.set("org.niis.xroad.proxy.application.testsuite.ProxyTestSuite")
  classpath = sourceSets.test.get().runtimeClasspath
}

project.extensions.getByType<JacocoPluginExtension>().applyTo(tasks.named<JavaExec>("runProxyTest").get())
