plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-core"))
  implementation(project(":common:common-domain"))
  implementation(project(":lib:globalconf-core"))
  implementation(project(":common:common-mail"))
  implementation(project(":service:signer:signer-api"))

  implementation(libs.acme4j)

  implementation(platform(libs.springBoot.bom))

  implementation("org.springframework.boot:spring-boot-starter-web")

  testImplementation(project(":common:common-test"))
}
