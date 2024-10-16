
####
#
# Temporary workaround to build example-adapter locally.
# Can be removed once the example-adapter for all architectures is published to a public repository.
#
####


################################################################################
FROM openjdk:17-jdk-slim AS build
RUN apt-get update && apt-get install -y git
WORKDIR /app
RUN git clone https://github.com/nordic-institute/xrd4j.git
WORKDIR /app/xrd4j/example-adapter
RUN ./gradlew assemble

RUN cp /app/xrd4j/example-adapter/build/libs/example-adapter-*-boot.war /app/example-adapter.war

################################################################################
# copy/paste from xrd4j repo
FROM eclipse-temurin:17-jre AS final

# Add Rest Gateway jar to container
COPY --from=build /app/example-adapter.war example-adapter.war

# Entry in json format
ENTRYPOINT ["java", "-jar", "/example-adapter.war"]

# Expose Tomcat
EXPOSE 8080
