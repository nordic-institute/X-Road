ARG REGISTRY=localhost:5555
FROM eclipse-temurin:21-jre

# Optimize APT for flaky mirrors (e.g. ports.ubuntu.com)
RUN echo 'Acquire::Connect::Timeout "5";' > /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::http::Timeout "10";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::https::Timeout "10";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::Retries "3";' >> /etc/apt/apt.conf.d/99timeouts && \
    echo 'Acquire::ForceIPv4 "true";' > /etc/apt/apt.conf.d/99force-ipv4

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

RUN curl -L -o /opt/jmx_prometheus_javaagent.jar \
      https://github.com/prometheus/jmx_exporter/releases/download/1.5.0/jmx_prometheus_javaagent-1.5.0.jar

# Copy license files from build context
COPY --from=build /LICENSE.txt /opt/app/LICENSE.txt
COPY --from=build /3RD-PARTY-NOTICES.txt /opt/app/3RD-PARTY-NOTICES.txt