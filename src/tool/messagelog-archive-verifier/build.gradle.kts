plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
  id("xroad.maven-publish-conventions")
}

dependencies {
  implementation(project(":common:common-core"))
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
    from(rootProject.file("3RD-PARTY-NOTICES.txt")) { into("META-INF/xroad") }
  }
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      from(components["shadow"])

      artifactId = "messagelog-archive-verifier"
    }
  }
}
