plugins {
  java
  `java-library`
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  testImplementation(platform(libs.findLibrary("quarkus-bom").get()))
  testImplementation("org.jboss.logmanager:jboss-logmanager")
  testImplementation("org.jboss.slf4j:slf4j-jboss-logmanager")
}

// Quarkus's EmptyLogConfiguratorFactory (priority 50) in quarkus-bootstrap-runner
// prevents logging.properties from being read by JBoss LogManager.
// Exclude it so PropertyLogContextConfigurator (priority 100) reads our logging.properties.
// This convention is only applied to library modules running plain unit tests,
// never to Quarkus application modules that need QuarkusDelayedHandler for @QuarkusTest.
configurations.testRuntimeClasspath {
  exclude(group = "io.quarkus", module = "quarkus-bootstrap-runner")
}

tasks {
  test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  }
}
