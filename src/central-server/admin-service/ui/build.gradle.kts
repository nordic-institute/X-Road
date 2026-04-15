plugins {
    id("base")
    id("xroad.module-conventions")
}

// This is a pnpm frontend module with no JVM dependencies — opt out of
// the project-wide dependency locking configured in the root build.gradle.kts.
dependencyLocking {
    unlockAllConfigurations()
}

configurations {
    create("dist")
}

tasks.register("assembleArtifacts") {
    dependsOn(":shared-ui:build-pnpm-workspace")
}

tasks.clean {
    delete(file("dist"))
}

// This is pnpm workspace project, it is built from root.
tasks.assemble {
    dependsOn("assembleArtifacts")
}

artifacts {
    add("dist", file("dist/")) {
        builtBy("assembleArtifacts")
    }
}
