import org.siouan.frontendgradleplugin.infrastructure.gradle.RunPnpmTaskType

plugins {
  alias(libs.plugins.frontendJDK21)
}

frontend {
  nodeVersion.set(project.property("frontendNodeVersion").toString())
  nodeInstallDirectory.set(file("${rootDir}/.gradle/pnpm-node/${project.property("frontendNodeVersion")}"))
  cacheDirectory.set(file("${projectDir}/.gradle/pnpm-cache"))
  //nodeDistributionUrlRoot.set("https://artifactory.niis.org/artifactory/nodejs-dist-remote/")
  maxDownloadAttempts.set(3)

  corepackVersion.set("0.34.0")
  packageJsonDirectory.set(file("${rootDir}/"))
  if (System.getenv().containsKey("CI")) {
    installScript.set("install --frozen-lockfile")
  }
}

tasks.register<RunPnpmTaskType>("build-pnpm-workspace") {
  dependsOn(tasks.named("installFrontend"))
  args.set("run build-workspace")
}

tasks.register<RunPnpmTaskType>("checkFrontAudit") {
  dependsOn(tasks.named("assembleFrontend"))
  args.set("run npx-check-audit")
}

tasks.register<RunPnpmTaskType>("test") {
  dependsOn(tasks.named("installFrontend"))
  args.set("run test-ss")
}

if (project.hasProperty("frontend-npm-audit")) {
  tasks.assemble {
    dependsOn(tasks.named("checkFrontAudit"))
  }
}

tasks.register<RunPnpmTaskType>("checkFrontWorkspaceLicense") {
  dependsOn(tasks.named("installFrontend"))
  args.set("run -r license-check")
}

tasks.named("checkFrontend") {
  dependsOn(tasks.named("checkFrontWorkspaceLicense"))
}

tasks.clean {
  delete(file("dist"))
}
