import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.protobuf)
}

sourceSets {
  main {
    java.srcDirs(
      "build/generated-sources",
      "build/generated/source/proto/main/grpc",
      "build/generated/source/proto/main/java"
    )
  }
}

dependencies {
  implementation(project(":common:common-domain"))
  api(project(":common:common-rpc"))

  testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
  protoc {
    artifact = libs.protobuf.protoc.get().toString()
  }
  plugins {
    create("grpc") {
      artifact = libs.grpc.protocGenGrpcJava.get().toString()
    }
  }
  generateProtoTasks {
    all().forEach {
      it.plugins {
        create("grpc") {
          option("@generated=omit")
        }
      }
    }
  }
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

    when {
      Os.isArch("x86_64") || Os.isArch("amd64") -> {
        outputs.dir(file("../../../libs/passwordstore/amd64"))
      }

      Os.isArch("aarch64") || Os.isArch("arm64") -> {
        outputs.dir(file("../../../libs/passwordstore/arm64"))
      }
    }

    workingDir = file("../../../")
    commandLine("make", "clean", "all")
  }

  val makeClean by tasks.registering(Exec::class) {
    workingDir = file("../../../")
    commandLine("make", "clean")
  }
}
