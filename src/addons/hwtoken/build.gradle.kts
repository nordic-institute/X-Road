plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":service:signer:signer-core"))
  implementation(project(":service:signer:signer-api"))
  implementation(project(":common:common-domain"))
}
