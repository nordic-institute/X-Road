plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  api(project(":lib:rpc-core"))
  implementation(libs.okhttp)
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
}

tasks.compileJava {
  dependsOn(tasks.named("generateProto"))
}
