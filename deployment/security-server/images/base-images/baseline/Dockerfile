ARG REGISTRY=localhost:5555
ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}eclipse-temurin:21-jre

RUN groupadd --system --gid 999 xroad && \
    useradd --system  \
    --uid 999  \
    --no-create-home \
    --shell /usr/sbin/nologin  \
    --gid xroad xroad

RUN mkdir /var/log/xroad && \
    mkdir /var/lib/xroad && \
    mkdir /var/tmp/xroad && \
    mkdir /var/cache/xroad && \
    mkdir -p /etc/xroad/ssl && \
    mkdir -p /etc/xroad/globalconf && \
    chown -R xroad:xroad /var/log/xroad && \
    chown -R xroad:xroad /var/lib/xroad && \
    chown -R xroad:xroad /var/tmp/xroad && \
    chown -R xroad:xroad /var/cache/xroad && \
    chown -R xroad:xroad /etc/xroad

ARG GITHUB_URL=https://github.com/
ARG XROAD_MIRROR_USER
RUN --mount=type=secret,id=mirror_token \
    CURL_AUTH="" && \
    if [ -n "$XROAD_MIRROR_USER" ] && [ -f /run/secrets/mirror_token ]; then \
      CURL_AUTH="-u ${XROAD_MIRROR_USER}:$(cat /run/secrets/mirror_token)"; \
    fi && \
    curl -fsSL $CURL_AUTH -o /opt/jmx_prometheus_javaagent.jar \
      "${GITHUB_URL}prometheus/jmx_exporter/releases/download/1.5.0/jmx_prometheus_javaagent-1.5.0.jar"

# Copy license files from build context
COPY --from=build /LICENSE.txt /opt/app/LICENSE.txt
COPY --from=build /3RD-PARTY-NOTICES.txt /opt/app/3RD-PARTY-NOTICES.txt