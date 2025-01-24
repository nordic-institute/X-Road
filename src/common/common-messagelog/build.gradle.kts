plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":common:common-scheduler"))
  implementation(project(":lib:globalconf-model"))
  implementation(project(":lib:serverconf-model"))
  implementation(project(":asic-util"))

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
