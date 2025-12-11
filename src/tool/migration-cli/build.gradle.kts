plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
}

val schemaTargetDir = layout.buildDirectory.dir("generated/sources").get().asFile
val xjc by configurations.creating

sourceSets {
  main {
    java.srcDirs(schemaTargetDir)
  }
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(libs.jaxb.runtime)
  implementation(libs.apache.commonsLang3)
  implementation(libs.apache.commonsConfiguration2)

  implementation(libs.postgresql)
  implementation(libs.slf4j.api)
  implementation(libs.logback.classic)
  implementation(libs.bouncyCastle.bcpg)

  implementation(project(":common:common-domain"))
  implementation(project(":common:common-vault-spring"))
  implementation("org.yaml:snakeyaml")

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")

  xjc(libs.bundles.jaxb)
}

val mainClassName = "org.niis.xroad.configuration.migration.LegacyConfigMigrationCLI"

tasks.jar {
  manifest {
    attributes("Main-Class" to mainClassName)
  }
}

tasks.shadowJar {
  archiveClassifier.set("")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.build {
  dependsOn(tasks.shadowJar)
}

val createDirs by tasks.registering {
  doLast {
    schemaTargetDir.mkdirs()
  }
}

val xjcTask by tasks.registering {
  dependsOn(":common:common-domain:xjc")
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
        "binding" to "../../common/common-domain/src/main/resources/identifiers-bindings.xml"
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
