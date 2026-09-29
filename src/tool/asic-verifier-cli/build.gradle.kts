plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
  id("xroad.maven-publish-conventions")
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":lib:globalconf-impl"))
  implementation(project(":lib:asic-core"))
  implementation(libs.logback.classic)
  testImplementation(project(":common:common-test"))
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.asic.verifier.cli.AsicVerifierMain")
  }
  archiveBaseName.set("asicverifier")
  archiveClassifier.set("")
  archiveVersion.set("")
  from(rootProject.file("3RD-PARTY-NOTICES.txt")) { into("META-INF/xroad") }
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      from(components["shadow"])

      artifactId = "asicverifier"
    }
  }
}
