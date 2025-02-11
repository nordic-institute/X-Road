plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:asic-core"))

  api(project(":service:configuration-client:configuration-client-model")) //TODO this is due to diagnostic status, might be dropped
  // if messagelog moves to proxy

  testImplementation(project(":common:common-test"))
  testImplementation(libs.bouncyCastle.bcpg)
  testImplementation(libs.mockito.core)
}

tasks.register<Copy>("copyGpg") {
  description = "Copy GPG keys to build directory"
  group = "test"

  from("src/test/gpg")
  into("build/gpg")
}

tasks.test {
  dependsOn("copyGpg")
}
