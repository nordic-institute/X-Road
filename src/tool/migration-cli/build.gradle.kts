plugins {
  id("xroad.java-conventions")
  id("com.gradleup.shadow")
}

dependencies {
  implementation(platform(libs.springBoot.bom))

  implementation(libs.apache.commonsLang3)
  implementation(libs.apache.commonsConfiguration2)

  implementation(libs.postgresql)
  implementation(libs.slf4j.api)
  implementation(libs.logback.classic)
  implementation(libs.bouncyCastle.bcpg)

  implementation(project(":common:common-vault-spring"))
  implementation("org.yaml:snakeyaml")

  testImplementation(project(":common:common-test"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

val mainClassName = "org.niis.xroad.configuration.migration.LegacyConfigMigrationCLI"

tasks.jar {
  manifest {
    attributes("Main-Class" to mainClassName)
  }
}

tasks.shadowJar {
  archiveClassifier.set("")
  exclude("**/module-info.class")
  from(rootProject.file("LICENSE.txt"))
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
}

tasks.build {
  dependsOn(tasks.shadowJar)
}
