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
  testImplementation(libs.assertj.core)
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
}

val bootstrapSqlDir = rootProject.layout.projectDirectory.dir("../deployment/native-packages/src/xroad/common/base/usr/share/xroad/db")

tasks.test {
  inputs.files(fileTree(bootstrapSqlDir) { include("bootstrap-*.sql") })
    .withPropertyName("bootstrapSql")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  systemProperty("bootstrap.sql.dir", bootstrapSqlDir.asFile.absolutePath)
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
