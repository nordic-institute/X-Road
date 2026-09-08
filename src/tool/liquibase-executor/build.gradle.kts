plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
}

dependencies {
  implementation(libs.liquibase.core)
  implementation(libs.picocli)
  implementation(libs.postgresql)
  implementation(libs.slf4j.api)
  implementation(libs.logback.classic)

  compileOnly(project(":common:common-core"))

  testImplementation(libs.h2database)
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
