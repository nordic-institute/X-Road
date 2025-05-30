plugins {
    id("xroad.java-conventions")
}

dependencies {
    implementation(project(":common:common-message"))
    implementation(project(":central-server:management-service:core-api"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}
