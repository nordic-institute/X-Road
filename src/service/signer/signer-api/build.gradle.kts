import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":common:common-rpc"))
}

tasks.compileJava {
  dependsOn(tasks.named("generateProto"))
}
