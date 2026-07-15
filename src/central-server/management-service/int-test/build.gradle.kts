plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":tool:api-test-core"))
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":central-server:openapi-model"))
  intTestImplementation(testFixtures(project(":common:common-management-request")))

  intTestImplementation(project(":tool:liquibase-executor"))
  intTestImplementation(libs.liquibase.core)
  intTestImplementation(libs.postgresql)
  intTestImplementation(libs.mockserver.client)
}

intTestComposeEnv {
  images(
    "CS_IMG" to "central-server-dev"
  )
}

intTestShadowJar {
  archiveBaseName("central-server-management-int-test")
  mainClass("org.niis.xroad.cs.test.ConsoleIntTestRunner")
}

intTestPhasedSuite {
  phasedSuiteClass = "ManagementServiceIntTestSuite"
  productName = "Management Service"
}
