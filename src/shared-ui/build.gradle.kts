import org.siouan.frontendgradleplugin.infrastructure.gradle.RunPnpmTaskType

plugins {
  alias(libs.plugins.frontendJDK21)
}

frontend {
  nodeVersion.set(project.property("frontendNodeVersion").toString())
  nodeInstallDirectory.set(file("${rootDir}/.gradle/pnpm-node/${project.property("frontendNodeVersion")}"))
  cacheDirectory.set(file("${projectDir}/.gradle/pnpm-cache"))
  nodeDistributionUrlRoot.set("https://artifactory.niis.org/artifactory/nodejs-dist-remote/")
  maxDownloadAttempts.set(3)

  corepackVersion.set("0.34.4")
  packageJsonDirectory.set(file("${rootDir}/"))
  if (System.getenv().containsKey("CI")) {
    installScript.set("install --frozen-lockfile")
  }
}

tasks.register<RunPnpmTaskType>("build-pnpm-workspace") {
  description = "Builds the pnpm workspace including this project."
  group = "build"

  dependsOn(tasks.named("installFrontend"))
  args.set("run build-workspace")
}

tasks.register<RunPnpmTaskType>("checkFrontAudit") {
  description = "Runs npm audit on frontend pnpm workspace."
  group = "verification"

  dependsOn(tasks.named("assembleFrontend"))
  args.set("run npx-check-audit")
}

tasks.register<RunPnpmTaskType>("test") {
  description = "Runs frontend tests."
  group = "verification"

  dependsOn(tasks.named("assembleFrontend"), tasks.named("build-pnpm-workspace"))
  args.set("run test-ss")
}

if (project.hasProperty("frontend-npm-audit")) {
  tasks.assemble {
    dependsOn(tasks.named("checkFrontAudit"))
  }
}

tasks.register<RunPnpmTaskType>("checkFrontWorkspaceLicense") {
  description = "Checks licenses of frontend pnpm workspace."
  group = "verification"

  dependsOn(tasks.named("installFrontend"), tasks.named("build-pnpm-workspace"))
  args.set("run -r license-check")
}

tasks.named("checkFrontend") {
  dependsOn(tasks.named("checkFrontWorkspaceLicense"))
}

tasks.clean {
  delete(file("dist"))
}
