plugins {
  id("xroad.java-conventions")
  id("xroad.jib-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  annotationProcessor(libs.mapstructProcessor)
  annotationProcessor(libs.lombokMapstructBinding)

  implementation(project(":common:common-db"))
  implementation(project(":common:common-message"))
  implementation(project(":common:common-messagelog"))
  implementation(libs.bouncyCastle.bcpkix)
  implementation(libs.slf4j.api)
  implementation(libs.mapstruct)
}

val libsRef = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.named("jib") {
  dependsOn("prepareLicenseFiles")
}

jib {
  from {
    image = "liquibase:${libsRef.findVersion("liquibase").get()}"
  }
  to {
    image = "${project.property("xroadImageRegistry")}/ss-db-messagelog-init"
    tags = setOf(project.findProperty("xroadServiceImageTag")?.toString())
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
      "LIQUIBASE_COMMAND_CHANGELOG_FILE" to "changelog/messagelog-changelog.xml",
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
