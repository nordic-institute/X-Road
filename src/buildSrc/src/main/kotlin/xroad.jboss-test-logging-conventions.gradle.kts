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

tasks {
  test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  }
}
