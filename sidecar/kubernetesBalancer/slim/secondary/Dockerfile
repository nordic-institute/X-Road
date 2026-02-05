ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER"

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
COPY files/secondary-slim-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-secondary-entrypoint.sh /root/entrypoint.sh

# Cleanup mirror configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh

CMD ["/root/entrypoint.sh"]
