plugins {
  id("xroad.java-conventions")
  alias(libs.plugins.protobuf)
}

sourceSets {
  getByName("main") {
    java.srcDirs(
      "src/main/java",
      "build/generated-sources",
      "build/generated/source/proto/main/grpc",
      "build/generated/source/proto/main/java"
    )
  }
}

dependencies {
  api(project(":common:common-rpc"))
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

tasks.named("compileJava") {
  dependsOn("generateProto")
}
