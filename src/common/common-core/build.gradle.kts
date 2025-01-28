import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  id("xroad.java-conventions")
}

/**
 * This is xroad core library. It should only contain dependencies that are relevant to all xroad modules.
 */
dependencies {
  api(platform(libs.jackson.bom))

  api("com.fasterxml.jackson.core:jackson-databind")
  api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

  api(libs.jclOverSlf4j)
  api(libs.apache.xmlsec)
  api(libs.apache.commonsLang3)
  api(libs.apache.commonsText)
  api(libs.bouncyCastle.bcpkix)
  api(libs.guava)
  api(libs.jackson.annotations)
  api(libs.commons.io)
  api(libs.apache.commonsConfiguration2) {
    exclude(group = "org.apache.commons", module = "commons-text")
  }
  api(libs.apache.httpclient5)

  api(libs.jakarta.injectApi)
  api(libs.jakarta.bindApi)
  api(libs.jakarta.soapApi)
  api(libs.jakarta.servletApi)
  api(libs.jakarta.annotationApi)
  api(libs.opentelemetry.instrumentation.annotations)

  testImplementation(project(":common:common-test"))
}

fun gitCommitHash(): String? {
  val cmd = "git show -s --format=git%h --abbrev=7"
  return try {
    ProcessBuilder(cmd.split(" "))
      .start()
      .inputStream.bufferedReader()
      .readText().trim()
  } catch (e: Exception) {
    println("Could not execute git command: ${e.message}")
    null
  }
}

fun gitCommitDate(): String? {
  val cmd = "git show -s --format=%cd --date=format-local:%Y%m%d%H%M%S"
  return try {
    ProcessBuilder(cmd.split(" "))
      .directory(projectDir)
      .apply { environment()["TZ"] = "UTC" }
      .start()
      .inputStream.bufferedReader()
      .readText().trim()
  } catch (e: Exception) {
    println("Could not execute git command: ${e.message}")
    null
  }
}

tasks.processResources {
  filesMatching("**/version.properties") {
    filter(
      mapOf(
        "tokens" to mapOf(
          "version" to project.property("xroadVersion"),
          "buildType" to project.property("xroadBuildType"),
          "gitCommitDate" to gitCommitDate(),
          "gitCommitHash" to gitCommitHash()
        )
      ), ReplaceTokens::class.java
    )
  }
}
