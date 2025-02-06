plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  alias(libs.plugins.quarkus)
}

val buildType: String = project.findProperty("buildType")?.toString() ?: "native"

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      // Common properties
      put("quarkus.package.output-directory", "libs")
      put("quarkus.package.output-name", "proxy-1.0")

      when (buildType) {
        "native" -> {
          put("quarkus.package.jar.type", "uber-jar")
          put("quarkus.package.jar.add-runner-suffix", "false")
        }

        "containerized" -> {
          put("quarkus.container-image.build", "true")
          put("quarkus.container-image.group", "niis")
          put("quarkus.container-image.name", "xroad-proxy")
          put("quarkus.container-image.tag", "latest")
        }

        else -> error("Unsupported buildType: $buildType. Use 'native' or 'containerized'")
      }
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(project(":lib:bootstrap-quarkus"))

  implementation(project(":service:proxy:proxy-core"))

//  testImplementation(libs.hsqldb)
  testImplementation(libs.restAssured)
  testImplementation(libs.apache.httpasyncclient)
//  testImplementation(project(":common:common-domain"))
  testImplementation(project(":common:common-jetty"))
  testImplementation(project(":common:common-message"))
  testImplementation(project(":common:common-test"))

  testImplementation(testFixtures(project(":lib:globalconf-impl")))
  testImplementation(testFixtures(project(":lib:serverconf-impl")))
  testImplementation(testFixtures(project(":lib:keyconf-impl")))
  testImplementation(testFixtures(project(":service:proxy:proxy-core")))

  testImplementation(libs.quarkus.junit5)
}

val testJar by tasks.registering(Jar::class) {
  archiveClassifier.set("test")
  from(sourceSets.test.get().output)
}

tasks.test {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

configurations {
  create("testArtifacts") {
    extendsFrom(configurations.testRuntimeOnly.get())
  }
}

artifacts {
  add("testArtifacts", testJar)
}

//val runProxyTest by tasks.registering(JavaExec::class) {
//  group = "verification"
//  shouldRunAfter(tasks.test)
//  jvmArgs(
//    "-Xmx2g",
//    "-Dxroad.proxy.ocspCachePath=build/ocsp-cache",
//    "-Dxroad.tempFiles.path=build/attach-tmp",
//    "-Dxroad.proxy.jetty-serverproxy-configuration-file=src/test/serverproxy.xml",
//    "-Dxroad.proxy.jetty-ocsp-responder-configuration-file=src/test/ocsp-responder.xml",
//    "-Dxroad.proxy.jetty-clientproxy-configuration-file=src/test/clientproxy.xml",
//    "-Dxroad.proxy.client-connector-so-linger=-1",
//    "-Dxroad.proxy.client-httpclient-so-linger=-1",
//    "-Dxroad.proxy.server-connector-so-linger=-1",
//    "-Dlogback.configurationFile=src/test/logback-proxytest.xml",
//    "-Dxroad.common.grpc-internal-tls-enabled=false"
//    // "-Djava.security.properties==src/main/resources/java.security"
//  )
//
//  mainClass.set("org.niis.xroad.proxy.application.testsuite.ProxyTestSuite")
//  classpath = sourceSets.test.get().runtimeClasspath
//}

//project.extensions.getByType<JacocoPluginExtension>().applyTo(tasks.named<JavaExec>("runProxyTest").get())
