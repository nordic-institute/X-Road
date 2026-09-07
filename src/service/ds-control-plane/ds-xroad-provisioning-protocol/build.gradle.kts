plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  api(project(":lib:rpc-core"))
}

tasks.compileJava {
  dependsOn(tasks.named("generateProto"))
}
