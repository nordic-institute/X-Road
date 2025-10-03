plugins {
  java
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
