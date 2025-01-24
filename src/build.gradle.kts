plugins {
  alias(libs.plugins.sonarqube)
  id("jacoco-report-aggregation")
  id("java")
  alias(libs.plugins.jandex) apply false
}

version = "1.0"
group = "org.niis.xroad"

sonarqube {
  properties {
    property("sonar.host.url", project.findProperty("sonarqubeHost") ?: "")
    property("sonar.projectKey", project.findProperty("sonarqubeProjectKey") ?: "")
    property("sonar.organization", project.findProperty("sonarqubeOrganization") ?: "")
    property("sonar.projectName", "X-Road")
    property("sonar.projectDescription", "Data Exchange Layer")
    property("sonar.projectVersion", project.findProperty("xroadVersion") ?: "")
    property("sonar.exclusions", "**/build/generated-sources/**")
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${rootProject.layout.buildDirectory.get().asFile}/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"
    )

    property("sonar.issue.ignore.multicriteria", "e1")
    // ignore 'Local-Variable Type Inference should be used"
    property("sonar.issue.ignore.multicriteria.e1.ruleKey", "java:S6212")
    property("sonar.issue.ignore.multicriteria.e1.resourceKey", "**/*.java")
  }
}

tasks.clean {
  delete("${rootDir}/packages/build")
}

dependencies {
  subprojects {
    pluginManager.withPlugin("java") {
      jacocoAggregation(project)
    }
  }
}

reporting {
  reports {
    create("jacocoAggregatedReport", JacocoCoverageReport::class) {
      testType.set("full")
      reportTask {
        description = "Build a full test coverage report including test and integrationTest results"
        project.subprojects {
          pluginManager.withPlugin("jacoco") {
            executionData(tasks.withType<Test>())
          }
        }
        reports {
          xml.required.set(true)
        }
      }
    }
  }
}

tasks.withType<Jar>().configureEach {
  enabled = false
}

// Register git-hooks
tasks.register<Copy>("installGitHooks") {
  description = "Install git hooks"
  group = "build"

  from(file("$rootDir/../.githooks"))
  into(file("$rootDir/../.git/hooks"))
  filePermissions {
    user {
      read = true
      write = true
      execute = true
    }
    group {
      read = true
      write = true
      execute = true
    }
    other {
      read = true
      execute = false
    }
  }
}

tasks.named("assemble") {
  dependsOn("installGitHooks")
}

tasks.named("sonar") {
  dependsOn("testCodeCoverageReport")
  onlyIf { System.getenv("SONAR_TOKEN") != null }
}
