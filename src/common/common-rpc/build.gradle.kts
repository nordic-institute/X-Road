plugins {
  id("xroad.java-conventions")
  id("xroad.test-fixtures-conventions")
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
  implementation(project(":common:common-properties"))
  implementation(libs.slf4j.api)

  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.util)
  api(libs.grpc.nettyShaded)
  api(libs.protobuf.javaUtil)
  api(libs.jakarta.annotationApi)

  implementation(libs.quarkus.springBoot.di)

  testFixturesImplementation(libs.quarkus.junit5)
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
