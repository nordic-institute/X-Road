plugins {
  id("xroad.java-conventions")
}

dependencies {
  // canonical TLS cipher/protocol defaults reused by ProxyConfigKeys (DefaultTlsProperties)
  implementation(project(":lib:properties-core"))

  testImplementation(libs.assertj.core)
}
