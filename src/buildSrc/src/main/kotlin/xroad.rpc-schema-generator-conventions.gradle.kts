plugins {
  id("com.google.protobuf")
  java
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

sourceSets {
  getByName("main") {
    java.srcDirs(
      "build/generated-sources",
      "build/generated/source/proto/main/grpc",
      "build/generated/source/proto/main/java"
    )
  }
}

protobuf {
  protoc {
      artifact = "com.google.protobuf:protoc:${libs.findVersion("protoc").get()}"
  }
  plugins {
      create("grpc") {
          artifact = "io.grpc:protoc-gen-grpc-java:${libs.findVersion("grpc").get()}"
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
