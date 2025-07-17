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
  api(project(":common:common-rpc"))
  implementation(project(":common:common-message"))
  implementation(project(":lib:serverconf-core"))
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
