plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":lib:properties-api"))
  api(project(":lib:rpc-core"))
  api(project(":service:signer:signer-api"))
}
