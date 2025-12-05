plugins {
  id("xroad.java-conventions")
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

configurations {
  create("xjc")
}

sourceSets {
  main {
    java.srcDirs("src/main/java", schemaTargetDir)
  }
}

dependencies {
  implementation(libs.jaxb.runtime)
  add("xjc", libs.bundles.jaxb)
}

tasks.register("createDirs") {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

tasks.register("xjc") {
  inputs.files(fileTree("src/main/resources") { include("*.xsd") })
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to configurations["xjc"].asPath
      )

      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "org.niis.xroad.proxymonitor.message",
        "schema" to "src/main/resources/monitoring.xsd",
        "binding" to "src/main/resources/jaxb-bindings.xml"
      )
    }
  }
}

tasks.named("xjc") {
  dependsOn("createDirs")
}

tasks.compileJava {
  dependsOn(tasks.named("xjc"))
}
