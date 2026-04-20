plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":central-server:admin-service:core"))
  implementation(project(":common:common-domain"))

  api(libs.springBoot.starterDataJpa)
  api(libs.hibernate.core)
  implementation("org.hibernate.validator:hibernate-validator")
}
