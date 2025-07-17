plugins {
    id("xroad.java-conventions")
}

dependencies {
    api(platform(libs.springCloud.bom))
    api(project(":central-server:openapi-model"))

    implementation(project(":common:common-core"))

    compileOnly(libs.jakarta.servletApi)

    api("org.springframework.cloud:spring-cloud-starter-openfeign")
    api(libs.feign.hc5)
}
