plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-message"))
  implementation(project(":lib:globalconf-impl"))
  implementation(libs.antlrST4)

  testRuntimeOnly(libs.junit.platform.launcher)

  api(libs.apache.httpasyncclient)
  api(libs.mockito.jupiter)
  api(libs.awaitility)
  api(libs.systemRules)
  api(libs.assertj.core)
  api(libs.logback.classic)
}

sourceSets {
  main {
    resources {
      srcDir("src/test/certs")
    }
  }
}
