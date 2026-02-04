import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  jacoco
  checkstyle
  id("com.societegenerale.commons.plugin.gradle.ArchUnitGradlePlugin")
  id("xroad.java-config-conventions")
  id("xroad.module-conventions")
}

val mockitoAgent = configurations.maybeCreate("mockitoAgent")
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  testImplementation(libs.findLibrary("junit-jupiterEngine").get())
  testImplementation(libs.findLibrary("junit-vintageEngine").get())

  testImplementation(libs.findLibrary("mockito-core").get())
  testImplementation(libs.findLibrary("mockito-jupiter").get())

  testRuntimeOnly(libs.findLibrary("junit.platform.launcher").get())

  mockitoAgent(libs.findLibrary("mockito-core").get()) { isTransitive = false }

  "archUnitExtraLib"(project(":arch-rules"))
}

tasks.withType<Test>() {
  useJUnitPlatform()

  systemProperty("file.encoding", "UTF-8")

  testLogging {
    events(TestLogEvent.FAILED)

    showStandardStreams = true
  }
  reports {
    junitXml.includeSystemOutLog = false // defaults to true
  }

  jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

checkstyle {
  toolVersion = libs.findVersion("checkstyle").get().toString()
  configDirectory.set(file("${project.rootDir}/config/checkstyle"))
  isIgnoreFailures = false
  isShowViolations = false
  enableExternalDtdLoad = true
}

tasks.named<Checkstyle>("checkstyleMain") {
  source = fileTree("src/main/java")
  configFile = file("${project.rootDir}/config/checkstyle/checkstyle.xml")
  classpath = files()
}

tasks.named<Checkstyle>("checkstyleTest") {
  source = fileTree("src/test/java")
  configFile = file("${project.rootDir}/config/checkstyle/checkstyle-test.xml")
}

tasks.withType(JacocoReport::class) {
  executionData(tasks.withType<Test>())
  reports {
    xml.required.set(true)
  }
}

jacoco {
  toolVersion = libs.findVersion("jacoco").get().toString()
}

tasks.named("jacocoTestReport") {
  enabled = false
}

archUnit {

  preConfiguredRules = listOf(
// These rules are disabled in preparation for X-Road 8
//    "org.niis.xroad.arch.rule.NoBeanAnnotationWithInitDestroy",
    "org.niis.xroad.arch.rule.NoPostConstructAnnotation",
    "org.niis.xroad.arch.rule.NoPreDestroyAnnotation",
    "org.niis.xroad.arch.rule.NoVanillaExceptions",
    "org.niis.xroad.arch.rule.NoQuarkusTestAnnotations"
  )
}

tasks.named("checkRules") {
  dependsOn("assemble")
}
