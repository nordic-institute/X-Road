import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  id("com.github.hierynomus.license")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  compileOnly(libs.findLibrary("lombok").get())
  annotationProcessor(libs.findLibrary("lombok").get())

  testCompileOnly(libs.findLibrary("lombok").get())
  testAnnotationProcessor(libs.findLibrary("lombok").get())

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


license {
  header = rootProject.file("LICENSE.txt")
  include("**/*.java")
  mapping("java", "SLASHSTAR_STYLE")
  strictCheck = true
  skipExistingHeaders = true
}

tasks.named<LicenseCheck>("licenseMain") {
  source = fileTree("src/main")
}

tasks.named<LicenseCheck>("licenseTest") {
  source = fileTree("src/test")
}

tasks.named<LicenseFormat>("licenseFormatMain") {
  source = fileTree("src/main")
}

tasks.named<LicenseFormat>("licenseFormatTest") {
  source = fileTree("src/test")
}
