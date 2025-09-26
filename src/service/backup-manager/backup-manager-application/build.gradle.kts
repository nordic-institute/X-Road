plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      val baseImageTag = project.findProperty("baseImageTag") ?: "latest"
      put("quarkus.jib.base-jvm-image", "${project.property("xroadImageRegistry")}/base-images/ss-baseline-backup-manager-runtime:${baseImageTag}")
      put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-backup-manager")
    }
  )
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:bootstrap-quarkus"))
  implementation(project(":common:common-rpc-quarkus"))
  implementation(project(":service:backup-manager:backup-manager-core"))

  implementation(libs.bundles.quarkus.containerized)
  implementation(libs.quarkus.extension.systemd.notify)

  testImplementation(libs.quarkus.junit5)
  testImplementation(project(":common:common-test"))
}
