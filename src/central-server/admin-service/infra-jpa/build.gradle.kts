plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(project(":central-server:admin-service:core"))
  implementation(project(":common:common-domain"))

  api("org.springframework.boot:spring-boot-starter-data-jpa")
  api(libs.hibernate.core)
  implementation("org.hibernate.validator:hibernate-validator")
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
