plugins {
  `java-library`
}

dependencies {
  api(project(":common:common-core"))
  api(project(":common:common-properties"))

  api(libs.bundles.quarkus.core)
}
