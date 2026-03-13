ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=configure-mirror-openbao-deb.sh,target=/tmp/configure-mirror-openbao-deb.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER" && \
    /bin/bash /tmp/configure-mirror-openbao-deb.sh

RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync

RUN adduser --system --shell /bin/bash --home /home/xroad-slave --ingroup xroad xroad-slave \
  && mkdir -m 550 -p /home/xroad-slave/.ssh \
  && chown xroad-slave /home/xroad-slave/.ssh \
  && crudini --set /etc/xroad/conf.d/node.ini node type 'master' \
  && chown xroad:xroad /etc/xroad/conf.d/node.ini \
  && cp -a /etc/xroad/conf.d/node.ini /usr/share/xroad/config/etc/xroad/conf.d/ \
  && crudini --set /etc/xroad/conf.d/override-docker.ini proxy server-support-clients-pooled-connections 'false' \
  && cp -a /etc/xroad/conf.d/override-docker.ini /usr/share/xroad/config/etc/xroad/conf.d/ \
  && echo "StrictModes no" >>/etc/ssh/sshd_config \
  && echo "PasswordAuthentication no" >>/etc/ssh/sshd_config \
  && echo "X11Forwarding no" >>/etc/ssh/sshd_config \
  && rm -f /etc/ssh/ssh_host*key*

COPY files/balancer-primary-entrypoint.sh /root/entrypoint.sh
COPY files/primary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf

# Cleanup mirror configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-openbao-deb.sh,target=/tmp/cleanup-mirror-openbao-deb.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh && \
    /bin/bash /tmp/cleanup-mirror-openbao-deb.sh

CMD ["/root/entrypoint.sh"]
