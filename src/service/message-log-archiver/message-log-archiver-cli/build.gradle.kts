plugins {
  id("xroad.java-conventions")
  id("xroad.quarkus-application-conventions")
}

configurations.named("implementation") {
  exclude(module = "globalconf-impl") // asic-core brings globalconf-impl transitively at runtime;
  exclude(module = "xml-apis") // conflicts with JDK's javax.xml; its TransformerFactory requires xalan
}

dependencies {
  implementation(platform(libs.quarkus.bom))
  implementation(libs.bundles.quarkus.core)

  implementation(project(":lib:properties-quarkus"))
  implementation(project(":service:message-log-archiver:message-log-archiver-core"))
}

tasks.jar {
  enabled = false
}
