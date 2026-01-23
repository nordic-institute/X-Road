
####
#
# Temporary workaround to build example-adapter locally.
# Can be removed once the example-adapter for all architectures is published to a public repository.
#
####


################################################################################
FROM eclipse-temurin:11 AS build-xrd4j-lib
RUN apt-get update && apt-get install -y git
WORKDIR /app
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dmaven.repo.local=/app/.m2_repository -Dgradle.user.home=/app/.gradle_home"
RUN <<EOF
  set -ex
  git clone https://github.com/nordic-institute/xrd4j.git
  cd xrd4j/src
  ./gradlew assemble publishToMavenLocal
EOF

#################################################################################
FROM eclipse-temurin:17 AS build-example-adapter
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dmaven.repo.local=/app/.m2_repository -Dgradle.user.home=/app/.gradle_home"
RUN --mount=type=cache,from=build-xrd4j-lib,source=/app,target=/app <<EOF
    set -ex
    cd /app/xrd4j/example-adapter
    ./gradlew assemble
    cp /app/xrd4j/example-adapter/build/libs/example-adapter-*-boot.war /example-adapter.war
EOF

################################################################################
# copy/paste from xrd4j repo
FROM eclipse-temurin:17-jre AS final

# Add Rest Gateway jar to container
COPY --from=build-example-adapter /example-adapter.war example-adapter.war

# Entry in json format
ENTRYPOINT ["java", "-jar", "/example-adapter.war"]

# Expose Tomcat
EXPOSE 8080
