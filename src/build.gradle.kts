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
        if (requested.group == "org.apache.tomcat.embed") {
          useVersion("11.0.22")
          because("XRDDEV-3176: Spring Boot 4.0.6 BOM pins tomcat-embed 11.0.21, vulnerable to CVE-2026-41284/41293/42498/43512/43513/43515.")
        }
        if (requested.group == "org.eclipse.jetty.ee10" && requested.name == "jetty-ee10-servlet") {
          useVersion("12.1.9")
          because("XRDDEV-3176: EDC ds-* modules pull jetty-ee10-servlet 12.1.6 via transitive (CVE-2026-2332, CVE-2026-5795); align with rest of Jetty pinned at 12.1.9.")
        }
        if (requested.group == "org.apache.httpcomponents.client5" && requested.name == "httpclient5") {
          useVersion("5.6.1")
          because("XRDDEV-3176: transitive 5.6 vulnerable to CVE-2026-40542; align with libs.versions.toml apache-httpclient5 = 5.6.1.")
        }
        if (requested.group == "io.netty" && requested.name.startsWith("netty-")
          && requested.version?.startsWith("4.2.") == true) {
          useVersion("4.2.14.Final")
          because("XRDDEV-3176: Quarkus 3.33.1.1 BOM ships Netty 4.2.12 which is vulnerable to CVE-2026-42577/79/81-87, CVE-2026-44248. 4.1.x lines untouched (gRPC shaded internal).")
        }
        if (requested.group == "io.opentelemetry.semconv" && requested.name == "opentelemetry-semconv") {
          useVersion("1.41.1")
          because("XRDDEV-3176: Quarkus 3.33.1.1 -> opentelemetry-instrumentation-api 2.23.0 -> opentelemetry-semconv 1.37.0 is vulnerable to CVE-2026-29181 and CVE-2026-39883. Forcing patched 1.41.1.")
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

tasks.register("dependencyAuditBackend") {
  description = "Runs OWASP dependency-check on backend dependencies."
  group = "verification"
  dependsOn("dependencyCheckAnalyze")
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
