plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.jandex)
}

dependencies {
  api(project(":common:common-db"))

  implementation(libs.quarkus.arc)
}

sourceSets {
  named("main") {
    resources {
      srcDir("../../signer/signer-jpa/src/main/resources/")
    }
  }
}

configurations {
  create("changelogJar")
}

tasks.register<Jar>("changelogJar") {
  archiveClassifier.set("resources")
  from(sourceSets.main.get().resources)
}

artifacts {
  add("changelogJar", tasks.named("changelogJar"))
}
