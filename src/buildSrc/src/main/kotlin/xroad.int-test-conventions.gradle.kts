plugins {
  java
}

sourceSets.create("intTest") {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}

configurations {
  val intTestImplementation by getting {
    extendsFrom(configurations.implementation.get())
  }
  val intTestRuntimeOnly by getting {
    extendsFrom(configurations.runtimeOnly.get())
  }
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  "intTestCompileOnly"(libs.findLibrary("lombok").get())
  "intTestAnnotationProcessor"(libs.findLibrary("lombok").get())
}
