plugins {
  java
}

val javaCompiler = project.javaToolchains.compilerFor(java.toolchain)
val javaHome = javaCompiler.get().metadata.installationPath.asFile.absolutePath

tasks.withType<JavaExec>().configureEach {
  systemProperty("file.encoding", "UTF-8")
  if (project.hasProperty("args")) {
    args = project.property("args").toString().split(" ")
  }
}

tasks.withType<Exec>().configureEach {
  environment("JAVA_HOME", javaHome)
  environment("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8")
}
