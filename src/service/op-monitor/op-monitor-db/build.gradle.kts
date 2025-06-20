plugins {
  id("xroad.java-conventions")
  id("xroad.jib-conventions")
}

dependencies {
}

configurations {
  create("changelogJar")
}

tasks.register<Jar>("changelogJar") {
  archiveClassifier.set("resources")
  from(sourceSets.main.get().resources)
}

artifacts {
  add("changelogJar", tasks.named("changelogJar"))
}

archUnit {
  setSkip(true)
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.named("jib") {
  dependsOn("prepareLicenseFiles")
}

jib {
  from {
    image = "liquibase:${libs.findVersion("liquibase").get()}"
  }
  to {
    image = "${project.property("xroadImageRegistry")}/ss-db-opmonitor-init"
    tags = setOf("latest")
  }
  container {
    entrypoint = listOf("/liquibase/docker-entrypoint.sh")
    workingDirectory = "/liquibase"
    user = "liquibase"
    args = listOf(
      "--log-level=debug",
      "update"
    )
    environment = mapOf(
      "LIQUIBASE_COMMAND_CHANGELOG_FILE" to "changelog/op-monitor-changelog.xml",
      "LIQUIBASE_COMMAND_DRIVER" to "org.postgresql.Driver",
    )

  }
  extraDirectories {
    paths {
      path {
        setFrom(project.file("src/main/resources/liquibase/").toPath())
        into = "/liquibase/changelog"
      }
      path {
        setFrom(layout.buildDirectory.dir("jib-extra/license"))
        into = "/liquibase/changelog"
      }
    }
  }
}

tasks {
  named("assemble") {
    dependsOn("jib")
  }
}
