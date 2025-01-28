plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(platform(libs.springBoot.bom))
  implementation("org.springframework.boot:spring-boot-starter")

  api(libs.quarkus.springBoot.di)
  api("io.smallrye.config:smallrye-config-core:3.10.2") //TODO test
}

////todo: enable
//archUnit {
//    skip = true
//}
