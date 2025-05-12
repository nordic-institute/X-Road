plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

//quarkus {
//  quarkusBuildProperties.putAll(
//    buildMap {
//      put("quarkus.container-image.image", "${project.property("xroadImageRegistry")}/ss-proxy")
//      put("quarkus.jib.jvm-entrypoint", "/bin/sh,/opt/app/entrypoint.sh")
//    }
//  )
//}


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
