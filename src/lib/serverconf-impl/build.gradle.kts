plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
}

val schema by configurations.creating

sourceSets {
  main {
    java.srcDirs(layout.buildDirectory.dir("generated-sources"))
  }
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

  schema(project(":common:common-domain"))
  schema(libs.apache.ant)
  schema(libs.hibernate.hikaricp)
  schema(libs.hibernate.toolsAnt)
  schema(libs.commons.collections)
  schema(libs.logback.classic)
  schema(libs.hsqldb)
}
