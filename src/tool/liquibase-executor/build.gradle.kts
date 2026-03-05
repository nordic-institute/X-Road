plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
}

dependencies {
  implementation(libs.liquibase.core)
  implementation("info.picocli:picocli:4.7.7")
  implementation(libs.postgresql)
  implementation(libs.slf4j.api)
  implementation(libs.logback.classic)
}

tasks.jar {
  enabled = false
}

tasks.shadowJar {
  manifest {
    attributes("Main-Class" to "org.niis.xroad.liquibase.LiquibaseExecutor")
  }
  archiveBaseName.set("liquibase-executor")
  archiveClassifier.set("")
  archiveVersion.set("")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
