ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION

# Configure Artifactory for APT
ARG ARTIFACTORY_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_CA_CERT

RUN --mount=type=bind,from=artifactory-scripts,source=configure-artifactory-apt.sh,target=/tmp/configure-artifactory-apt.sh \
    --mount=type=secret,id=artifactory_token \
    /bin/bash /tmp/configure-artifactory-apt.sh "$ARTIFACTORY_URL" "$ARTIFACTORY_USER" "$ARTIFACTORY_CA_CERT"

RUN apt-get -qq update \
  && apt-get -qq install rsync openssh-client cron

# Configure node pod for balancer
RUN crudini --set /etc/xroad/conf.d/node.ini node type 'slave' \
  && crudini --set /etc/xroad/conf.d/node.ini message-log archive-interval '0 * * ? * * 2099' \
  && chown xroad:xroad /etc/xroad/conf.d/node.ini \
  && cp -a /etc/xroad/conf.d/node.ini /usr/share/xroad/config/etc/xroad/conf.d/ \
  && rm -f /etc/cron.d/xroad-* /etc/cron.d/sysstat \
  && mkdir -m 0700 -p /home/xroad/.ssh \
  && chown xroad:xroad /home/xroad/.ssh

COPY files/xroad-slave-sync.logrotate /etc/logrotate.d/xroad-slave-sync
COPY files/secondary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-secondary-entrypoint.sh /root/entrypoint.sh

# Cleanup Artifactory configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=artifactory-scripts,source=cleanup-artifactory-apt.sh,target=/tmp/cleanup-artifactory-apt.sh \
    /bin/bash /tmp/cleanup-artifactory-apt.sh

CMD ["/root/entrypoint.sh"]
