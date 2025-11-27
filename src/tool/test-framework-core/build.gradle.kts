plugins {
  id("xroad.java-conventions")
}
dependencies {
  api(platform(libs.springCloud.bom))

  implementation(project(":common:common-properties"))

  api(libs.slf4j.api)
  api(libs.logback.classic)
  api(libs.julOverSlf4j)

  api(libs.cucumber.java)
  api(libs.cucumber.spring)
  api(libs.cucumber.junit)
  api(libs.smallrye.config.core)
  api(libs.smallrye.config.yaml)
  api(libs.junit.jupiterEngine)
  api(libs.junit.platform.console)
  api(libs.junit.platform.suiteApi)
  api(libs.junit.platform.suiteEngine)
  api(libs.testcontainers.core)
  api(libs.mockserver.client)

  api(libs.feign.hc5)
  api(libs.feign.jackson)
  api(libs.feign.slf4j)

  api(libs.hikariCP)

  api(libs.springFramework.context)
  api(libs.springFramework.web)
  api(libs.springFramework.test)
  api(libs.springFramework.jdbc)

  api("org.springframework.cloud:spring-cloud-starter-openfeign"){
    exclude(group = "org.springframework.boot")
  }

  api(libs.test.allure.cucumber7)
  api(libs.test.allure.selenide)
  api(libs.test.selenide.core)
  api(libs.test.selenide.proxy)

  api(libs.assertj.core)
}

archUnit {
  setSkip(true)
}
