ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}eclipse-temurin:25-jre-alpine

RUN apk add --no-cache bash \
    && addgroup -g 1001 liquibase && adduser -u 1001 -G liquibase -h /home/liquibase -s /bin/bash -D liquibase \
    && mkdir -p /var/log/xroad && chown liquibase:liquibase /var/log/xroad

COPY --from=build-artifacts /liquibase-executor.jar /app/liquibase-executor.jar

COPY --from=license /LICENSE.txt /app/LICENSE.txt
COPY --from=license /3RD-PARTY-NOTICES.txt /app/3RD-PARTY-NOTICES.txt

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER 1001
WORKDIR /app

ENTRYPOINT ["/app/entrypoint.sh"]
