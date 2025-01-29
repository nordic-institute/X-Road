plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":common:common-db"))
  api(project(":lib:serverconf-core"))
  api(project(":lib:globalconf-impl"))

  testImplementation(project(":common:common-test"))
  testImplementation(libs.hsqldb)
  testImplementation(libs.hibernate.hikaricp)

  testFixturesImplementation(project(":common:common-test"))
}
