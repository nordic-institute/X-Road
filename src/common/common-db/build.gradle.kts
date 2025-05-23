plugins {
  id("xroad.java-conventions")
}

dependencies {
  api(libs.hibernate.core)

  implementation(project(":common:common-core"))
  implementation(libs.hibernate.hikaricp)
  implementation(libs.hikariCP)
  implementation(libs.postgresql)

  // DB layer tests use HSQLDB with in-memory tables
  testImplementation(libs.hsqldb)
  testImplementation(project(":common:common-test"))
}
