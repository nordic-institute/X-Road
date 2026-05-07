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
  implementation(libs.quarkus.scheduler)
  implementation(libs.quarkus.health)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.web)
  implementation(libs.edc.spi.jsonld)
  implementation(libs.edc.spi.transform)
  implementation(libs.edc.spi.dataplane)
  implementation(libs.edc.spi.dataplane.selector)
  implementation(libs.edc.lib.transform)
  implementation(libs.edc.lib.jsonld)
  implementation(libs.edc.dataplane.signaling.api)
  implementation(libs.edc.dataplane.signaling.transform)
  implementation(libs.jersey.container.servlet)
  implementation(libs.jersey.inject.hk2)
  implementation(libs.jetty.ee11.servlet)

  testImplementation(project(":service:op-monitor:op-monitor-api"))
  testImplementation(libs.assertj.core)
  testImplementation(libs.mockito.core)
  testImplementation(libs.wiremock.standalone)
}
