ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}eclipse-temurin:25-jre-noble

RUN groupadd -g 1001 liquibase && useradd -u 1001 -g liquibase -m -s /bin/bash liquibase

COPY --from=build-artifacts /liquibase-executor.jar /app/liquibase-executor.jar

COPY --from=license /LICENSE.txt /app/LICENSE.txt
COPY --from=license /3RD-PARTY-NOTICES.txt /app/3RD-PARTY-NOTICES.txt

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER 1001
WORKDIR /app

ENTRYPOINT ["/app/entrypoint.sh"]
