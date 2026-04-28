plugins {
  alias(libs.plugins.sonarqube)
  alias(libs.plugins.owaspDependencyCheck)
  id("jacoco-report-aggregation")
  id("java")
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
      "${rootProject.layout.buildDirectory.get().asFile}/reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml"
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

allprojects {

  configurations.all {
    resolutionStrategy {
      eachDependency {
        if (requested.group == "jakarta.xml.bind" && requested.name == "jakarta.xml.bind-api") {
          useVersion("4.0.2")
          because("newer version will fail decoding base64 strings with white space. https://github.com/jakartaee/jaxb-api/issues/325")
        }
      }
    }
  }
}

reporting {
  reports {
    create("jacocoAggregatedReport", JacocoCoverageReport::class) {
      testSuiteName.set("full")
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
  dependsOn(tasks.named("jacocoAggregatedReport"))
  onlyIf { System.getenv("SONAR_TOKEN") != null }
}

dependencyCheck {
  formats = listOf("HTML", "JSON")
  failBuildOnCVSS = 11f // Never fail the build (max CVSS is 10.0) — report only
  suppressionFile = "config/owasp/suppressions.xml"
  autoUpdate = false
  nvd {
    apiKey = System.getenv("NVD_API_KEY") ?: ""
  }
  analyzers {
    nodeEnabled = false // Project uses pnpm, not npm — frontend audit handled separately
    ossIndexEnabled = false // Avoid Sonatype OSS Index rate limits — NVD is sufficient
    nodeAuditEnabled = false
  }
}

tasks.register("dependencyAuditBackend") {
  description = "Runs OWASP dependency-check on backend dependencies."
  group = "verification"
  dependsOn("dependencyCheckAnalyze")
}

tasks.register("dependencyAudit") {
  description = "Runs dependency security audit on all frontend and backend dependencies."
  group = "verification"
  dependsOn(":shared-ui:dependencyAuditFrontend", "dependencyAuditBackend")
}
