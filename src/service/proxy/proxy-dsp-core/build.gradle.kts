plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(platform(libs.quarkus.bom))
  implementation(libs.quarkus.arc)
  implementation(libs.jetty.server)
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  implementation(project(":service:op-monitor:op-monitor-api"))

  implementation("com.github.ben-manes.caffeine:caffeine")
  implementation(libs.edc.dataplane.sdk)
  implementation(libs.jersey.container.servlet)
  implementation(libs.jersey.inject.hk2)
  implementation(libs.jetty.ee11.servlet)

  testImplementation(project(":service:op-monitor:op-monitor-api"))
  testImplementation(libs.assertj.core)
}
