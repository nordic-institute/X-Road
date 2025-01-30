import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  id("xroad.java-conventions")
  id("xroad.rpc-schema-generator-conventions")
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":common:common-rpc"))
}

tasks.compileJava {
  dependsOn(tasks.named("generateProto"))
}

tasks.test {
  when {
    Os.isArch("x86_64") || Os.isArch("amd64") -> {
      jvmArgs("-Djava.library.path=../../../libs/passwordstore/amd64")
    }

    Os.isArch("aarch64") || Os.isArch("arm64") -> {
      jvmArgs("-Djava.library.path=../../../libs/passwordstore/arm64")
    }
  }
}

if (Os.isName("linux")) {
  val make by tasks.registering(Exec::class) {
    val javaCompiler = javaToolchains.compilerFor(java.toolchain)
    val javaHome = javaCompiler.get().metadata.installationPath.asFile.absolutePath

    inputs.dir(fileTree("../../../passwordstore") {
      include("*.c", "*.h", "Makefile")
    })
    inputs.dir(file("${javaHome}/include"))
    outputs.dir(file("../../../lib"))

    workingDir = file("../../../")
    commandLine("make", "clean", "all")
  }

  val makeClean by tasks.registering(Exec::class) {
    workingDir = file("../../../")
    commandLine("make", "clean")
  }

  tasks.clean {
    dependsOn(makeClean)
  }
}
