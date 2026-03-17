plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.bom.dataplane.sql)
}

archUnit {
  setSkip(true)
}
