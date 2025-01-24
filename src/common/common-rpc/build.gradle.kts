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
  implementation(project(":common:common-core"))
  implementation(libs.slf4j.api)

  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.nettyShaded)
  api(libs.protobuf.javaUtil)
  api(libs.jakarta.annotationApi)
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
