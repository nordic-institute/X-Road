plugins {
    id("xroad.java-conventions")
}

dependencies {
    api(platform(libs.springBoot.bom))
    api(project(":common:common-management-request"))
    api(project(":common:common-message")) {
        exclude(group = "org.eclipse.jetty")
    }
}
