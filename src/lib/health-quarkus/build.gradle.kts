plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  implementation(libs.quarkus.health)
}

// All classes in this module are @ApplicationScoped, causing ArchUnit's
// empty-should check to fail (no non-@ApplicationScoped classes to verify).
tasks.named("checkRules") {
  enabled = false
}
