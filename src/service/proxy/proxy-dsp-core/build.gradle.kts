plugins {
  id("xroad.java-conventions")
  id("xroad.jboss-test-logging-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":service:proxy:proxy-core"))
  implementation(project(":lib:properties-core"))
  implementation(platform(libs.quarkus.bom))
  implementation(libs.quarkus.arc)
  implementation(libs.jetty.server)
  implementation(project(":lib:ds-identity-core"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":lib:serverconf-core"))
  implementation(project(":lib:rpc-core"))
  implementation(project(":service:ds-control-plane:ds-xroad-asset-access-protocol"))
  implementation(project(":service:op-monitor:op-monitor-api"))

  implementation(libs.quarkus.caffeine)
  implementation(libs.quarkus.scheduler)
  implementation(libs.quarkus.health)
  implementation(libs.edc.spi.core)
  implementation(libs.edc.spi.web)
  implementation(libs.edc.spi.jsonld)
  implementation(libs.edc.spi.dataplane)
  implementation(libs.edc.spi.dataplane.selector)
  implementation(libs.edc.core.dps)
  implementation(libs.jersey.container.servlet)
  implementation(libs.jersey.inject.hk2)
  implementation(libs.jersey.media.json.processing)
  implementation(libs.jersey.media.json.jackson)
  implementation(libs.jetty.ee11.servlet)

  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.mockito.core)
  testImplementation(libs.wiremock.standalone)
}
