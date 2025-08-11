plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":common:common-core"))

  testRuntimeOnly(libs.junit.platform.launcher)
}

val mainClassName = "org.niis.xroad.cli.ArchiveHashChainVerifier"

tasks {
  jar {
    manifest {
      attributes["Main-Class"] = mainClassName
    }
    enabled = false
  }

  shadowJar {
    archiveVersion.set("")
    archiveClassifier.set("")
    from(rootProject.file("LICENSE.txt"))
  }

  build {
    dependsOn(shadowJar)
  }
}
