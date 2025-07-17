plugins {
  id("xroad.java-conventions")
}

val schemaTargetDir = layout.buildDirectory.dir("generated-sources").get().asFile

val xjc by configurations.creating

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
  }
}

dependencies {
  api(platform(libs.springBoot.bom))

  implementation(project(":common:common-core"))
  implementation(project(":common:common-jetty"))
  implementation(project(":lib:globalconf-spring"))
  implementation(project(":common:common-rpc"))
  implementation(project(":service:signer:signer-api"))

  api("org.springframework:spring-context-support")
  api(fileTree("../../../libs/pkcs11wrapper") { include("*.jar") })

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")



  xjc(libs.bundles.jaxb)
}

val createDirs by tasks.registering {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

val xjcTask by tasks.registering {
  dependsOn(":common:common-domain:xjc", ":lib:globalconf-core:xjc")
  inputs.files(files("src/main/resources/*.xsd"))
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
        "package" to "org.niis.xroad.signer.keyconf",
        "schema" to "src/main/resources/keyconf.xsd",
        "binding" to "../../../common/common-domain/src/main/resources/identifiers-bindings.xml"
      )
    }
  }
}

tasks.named("xjcTask") {
  dependsOn(createDirs)
}

tasks.compileJava {
  dependsOn(xjcTask)
}
