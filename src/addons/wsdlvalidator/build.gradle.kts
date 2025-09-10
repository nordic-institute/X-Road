plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.shadow)
}

val mainClassName = "ee.ria.xroad.wsdlvalidator.WSDLValidator"

tasks.jar {
  manifest {
    attributes("Main-Class" to mainClassName)
  }
}

dependencies {
  implementation(libs.apache.cxfToolsValidator)
  implementation(libs.apache.cxfRtTransportsHttp)
  implementation(libs.jakarta.annotationApi)
}

tasks.shadowJar {
  archiveClassifier.set("")
  exclude("**/module-info.class")
  append("META-INF/LICENSE")
  append("META-INF/LICENSE.txt")
  append("META-INF/NOTICE")
  append("META-INF/NOTICE.txt")
  append("META-INF/cxf/bus-extensions.txt")
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
