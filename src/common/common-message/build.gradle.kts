plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
}

val xjc by configurations.creating

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

sourceSets {
  main {
    java.srcDirs("src/main/java", schemaTargetDir)
  }
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-domain"))

  api(libs.apache.mime4jCore)
  api(libs.jaxb.runtime)
  api(libs.jetty.http)
  api(libs.apache.httpclient)
  api(libs.saajImpl)
  api(libs.woodstox.core)

  testImplementation(project(":common:common-test"))
  xjc(libs.bundles.jaxb)

  constraints {
    testImplementation("org.apache.james:apache-mime4j-core") {
      version {
        strictly(libs.apache.mime4jCore.get().version!!)
      }
      because("Avoid pulling in old versions via transitive dependencies")
    }
  }
}

tasks.register("xjc") {
  description = "Generate JAXB classes from XSD schemas"
  group = "build"

  inputs.files(fileTree("src/main/resources") { include("*.xsd") })
  outputs.dir(schemaTargetDir)

  doLast {
    ant.withGroovyBuilder {
      "taskdef"(
        "name" to "xjc",
        "classname" to "com.sun.tools.xjc.XJCTask",
        "classpath" to xjc.asPath
      )

      "xjc"(
        "destdir" to schemaTargetDir,
        "package" to "ee.ria.xroad.common.hashchain",
        "schema" to "src/main/resources/hashchain.xsd"
      )
    }
  }
}

tasks.compileJava {
  dependsOn(tasks.named("xjc"))
}
