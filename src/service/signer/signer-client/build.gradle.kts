plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":service:signer:signer-api"))
}
