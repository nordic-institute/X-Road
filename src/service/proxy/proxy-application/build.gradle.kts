plugins {
  id("xroad.java-conventions")
  id("xroad.java-exec-conventions")
  id("xroad.quarkus-application-conventions")
}

configurations.named("runtimeClasspath") {
  exclude(group = "xml-apis", module = "xml-apis")
}

configurations.named("runtimeClasspath") {
  // jnats, pulled in by EDC core-lib and unused, would add a second BouncyCastle provider jar
  exclude(group = "org.bouncycastle", module = "bcprov-lts8on")
}

dependencies {
  implementation(platform(libs.quarkus.bom))

  implementation(project(":lib:health-check-core"))
  implementation(project(":lib:properties-quarkus"))
  implementation(project(":lib:rpc-quarkus"))
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":service:proxy:proxy-dsp-core"))
  implementation(libs.bundles.quarkus.containerized)

}
