plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":common:common-properties"))

  implementation(libs.quarkus.jdbc.postgresql)
}
