plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(platform(libs.jackson.bom))
  implementation("tools.jackson.core:jackson-databind")

  implementation(libs.slf4j.api)
  implementation(libs.smallrye.config.core)
  implementation(libs.hikariCP)
  implementation(libs.apache.commonsLang3)

  testImplementation(libs.assertj.core)
  testImplementation(libs.h2database)
  testImplementation(libs.mockito.jupiter)
  testImplementation(libs.systemStubs)
  testImplementation(libs.junit.jupiter.params)

  testFixturesImplementation(libs.smallrye.config.core)
  testFixturesImplementation(libs.h2database)
  testFixturesImplementation(libs.mockito.core)
  testFixturesImplementation(libs.systemStubs)
}

tasks.test {
  val nodeNameScript = rootDir.resolve(
    "../deployment/native-packages/src/xroad/common/base/usr/share/xroad/scripts/_node_name.sh"
  ).canonicalFile
  inputs.files(nodeNameScript).withPropertyName("nodeNameScript")
  systemProperty("xroad.test.node-name-script", nodeNameScript.path)
}
