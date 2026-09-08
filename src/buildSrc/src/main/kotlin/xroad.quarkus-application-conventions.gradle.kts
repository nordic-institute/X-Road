plugins {
  id("io.quarkus")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  implementation(platform(libs.findLibrary("quarkus-bom").get()))
}

quarkus {
  quarkusBuildProperties.putAll(
    buildMap {
      put("quarkus.package.jar.type", "fast-jar")
    }
  )
}

tasks {
  named("compileJava") {
    dependsOn("compileQuarkusGeneratedSourcesJava")
  }
  test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  }
  jar {
    enabled = false
  }
}
