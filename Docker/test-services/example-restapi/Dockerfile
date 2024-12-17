# Builder
FROM eclipse-temurin:17 AS builder
WORKDIR /app
COPY . /app
RUN ./gradlew clean build

# Runner
FROM eclipse-temurin:17
COPY --from=builder /app/build/libs/restapi-*.*.*-SNAPSHOT.jar /app/example-restapi.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "example-restapi.jar"]
EXPOSE 8080
