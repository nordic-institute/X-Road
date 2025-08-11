import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  jacoco
  checkstyle
  id("com.github.hierynomus.license")
  id("com.societegenerale.commons.plugin.gradle.ArchUnitGradlePlugin")
  id("xroad.module-conventions")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

val mockitoAgent = configurations.maybeCreate("mockitoAgent")
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  testImplementation(libs.findLibrary("junit-jupiterEngine").get())
  testImplementation(libs.findLibrary("junit-vintageEngine").get())

  compileOnly(libs.findLibrary("lombok").get())
  annotationProcessor(libs.findLibrary("lombok").get())

  testCompileOnly(libs.findLibrary("lombok").get())
  testAnnotationProcessor(libs.findLibrary("lombok").get())

  testImplementation(libs.findLibrary("mockito-core").get())
  testImplementation(libs.findLibrary("mockito-jupiter").get())

  testRuntimeOnly(libs.findLibrary("junit.platform.launcher").get())

  mockitoAgent(libs.findLibrary("mockito-core").get()) { isTransitive = false }

  "archUnitExtraLib"(project(":arch-rules"))
}

tasks.withType<JavaCompile>() {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(
    listOf(
//            "-Xlint:unchecked",
//            "-Xlint:deprecation",
//            "-Xlint:rawtypes",
      "-Xlint:fallthrough",
      "-Xlint:finally",
      "-parameters"
    )
  )
}

tasks.withType<Javadoc>() {
  options.encoding = "UTF-8"
}

tasks.withType<Jar>() {
  from(rootProject.file("LICENSE.txt")) { into("META-INF") }
  duplicatesStrategy = DuplicatesStrategy.WARN
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

license {
  header = rootProject.file("LICENSE.txt")
  include("**/*.java")
  mapping("java", "SLASHSTAR_STYLE")
  strictCheck = true
  skipExistingHeaders = true
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseCheck>("licenseMain") {
  source = fileTree("src/main")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseCheck>("licenseTest") {
  source = fileTree("src/test")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseFormat>("licenseFormatMain") {
  source = fileTree("src/main")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseFormat>("licenseFormatTest") {
  source = fileTree("src/test")
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
  testScopePath = "/classes/java/main" // disabling default test scanning

  preConfiguredRules = listOf(
    "org.niis.xroad.arch.rule.NoBeanAnnotationWithInitDestroy",
// These rules are disabled in preparation for X-Road 8
//            "org.niis.xroad.arch.rule.NoPostConstructAnnotation",
//            "org.niis.xroad.arch.rule.NoPreDestroyAnnotation",
  )
}

tasks.named("checkRules") {
  dependsOn("assemble")
}
