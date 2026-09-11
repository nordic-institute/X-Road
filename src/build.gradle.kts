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
        if (requested.group == "org.eclipse.jetty.ee10" && requested.name == "jetty-ee10-servlet") {
          useVersion("12.1.9")
          because("XRDDEV-3176: EDC ds-* modules pull jetty-ee10-servlet 12.1.6 via transitive (CVE-2026-2332, CVE-2026-5795); align with rest of Jetty pinned at 12.1.9.")
        }
        if (requested.group == "org.apache.httpcomponents.client5" && requested.name == "httpclient5") {
          useVersion("5.6.1")
          because("XRDDEV-3176: transitive 5.6 vulnerable to CVE-2026-40542; align with libs.versions.toml apache-httpclient5 = 5.6.1.")
        }
        if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) {
          useVersion(libs.versions.bouncyCastle.get())
          because("Quarkus platform manages Bouncy Castle past 1.84; newer releases reject X.509 'C' RDN values " +
            "that aren't exactly 2 characters, but X-Road stores the X-Road instance identifier in that attribute, " +
            "which is not an ISO 3166 country code. Keep the project-wide pin until identifier handling is redesigned.")
        }
        if (requested.group == "org.hibernate.orm") {
          useVersion(libs.versions.hibernate.get())
          because("Keep org.hibernate.orm aligned to the catalog version so the Quarkus platform BOM " +
            "can't drift it in either direction.")
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

dependencyCheck {
  formats = listOf("HTML", "JSON")
  failBuildOnCVSS = 11f // Never fail the build (max CVSS is 10.0) — report only
  suppressionFile = "config/owasp/suppressions.xml"
  autoUpdate = (project.findProperty("nvdAutoUpdate")?.toString() ?: "true").toBoolean()

  nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""

  analyzers.ossIndex.enabled = false
  analyzers.nodeAudit.enabled = false
  analyzers.nodeAudit.pnpmEnabled = false
  analyzers.assemblyEnabled = false
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
  autoUpdate = (project.findProperty("nvdAutoUpdate")?.toString() ?: "true").toBoolean()

  nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""

  analyzers.ossIndex.enabled = false
  analyzers.nodeAudit.enabled = false
  analyzers.nodeAudit.pnpmEnabled = false
  analyzers.assemblyEnabled = false
}

tasks.register("dependencyAuditBackend") {
  description = "Runs OWASP dependency-check on backend dependencies."
  group = "verification"
  dependsOn("dependencyCheckAnalyze")
}
