plugins {
  id("xroad.java-conventions")
  id("xroad.int-test-conventions")
}

dependencies {
  intTestImplementation(project(":common:common-test"))
  intTestImplementation(project(":tool:api-test-core"))
  intTestImplementation(project(":service:signer:signer-client"))
  intTestImplementation(project(":common:common-core"))
  intTestImplementation(project(":common:common-message"))
  intTestImplementation(project(":lib:properties-core"))
}

intTestComposeEnv {
  env("XROAD_SECRET_STORE_ROOT_TOKEN", "root-token")
  env("XROAD_SECRET_STORE_TOKEN", "system-test-xroad-token")

  images(
    "OPENBAO_DEV_IMG" to "openbao-dev",
    "DB_INIT_IMG" to "ss-db-init",
    "SIGNER_IMG" to "ss-signer",
    "CA_IMG" to "testca-dev"
  )
}

intTestShadowJar {
  archiveBaseName("signer-int-test")
  mainClass("org.niis.xroad.signer.test.ConsoleIntTestRunner")
}

intTestPhasedSuite {
  phasedSuiteClass = "SignerIntTestSuite"
  productName = "Signer"
}

afterEvaluate {
  tasks.named<Test>("intTest") {
    dependsOn(":service:signer:signer-application:quarkusBuild")

    description = "Runs the signer integration test suite in scenario order on the shared signer + " +
        "secondary-signer stack (all classes share the same two tokens, so the suite runs class-ordered " +
        "and serial - see SignerIntTestSuite). Pass --tests <pattern> to run a single class/method " +
        "directly (IDE-friendly); the stack still boots via the LauncherSessionListener SPI."
  }
}

tasks.named<Copy>("processIntTestResources") {
  from("../../../../development/docker/testca-dev")
}
