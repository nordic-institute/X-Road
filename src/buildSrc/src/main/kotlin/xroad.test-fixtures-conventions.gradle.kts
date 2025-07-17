plugins {
  id("java-test-fixtures")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  "testFixturesCompileOnly"(libs.findLibrary("lombok").get())
  "testFixturesAnnotationProcessor"(libs.findLibrary("lombok").get())
}
