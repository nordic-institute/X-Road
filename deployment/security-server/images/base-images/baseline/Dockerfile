ARG REGISTRY=localhost:5555
FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 999 xroad && \
    useradd --system  \
    --uid 999  \
    --no-create-home \
    --shell /usr/sbin/nologin  \
    --gid xroad xroad

RUN mkdir /var/log/xroad && \
    mkdir /var/tmp/xroad && \
    mkdir /var/cache/xroad && \
    mkdir -p /etc/xroad/ssl && \
    mkdir -p /etc/xroad/globalconf && \
    chown -R xroad:xroad /var/log/xroad && \
    chown -R xroad:xroad /var/tmp/xroad && \
    chown -R xroad:xroad /var/cache/xroad && \
    chown -R xroad:xroad /etc/xroad

# Copy license files from build context
COPY --from=build /LICENSE.txt /opt/app/LICENSE.txt
COPY --from=build /3RD-PARTY-NOTICES.txt /opt/app/3RD-PARTY-NOTICES.txt