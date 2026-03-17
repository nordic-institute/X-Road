plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.virtual.controlplane.feature.sql.bom)
}

archUnit {
  setSkip(true)
}
