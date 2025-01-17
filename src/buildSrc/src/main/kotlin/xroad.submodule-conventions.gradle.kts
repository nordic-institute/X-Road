plugins {
  id("xroad.module-conventions")
}
tasks.withType<Jar>().configureEach {
  enabled = false
}
