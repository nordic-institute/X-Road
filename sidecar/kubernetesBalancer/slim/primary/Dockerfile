ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim

# Configure Artifactory for APT
ARG ARTIFACTORY_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_CA_CERT

RUN --mount=type=bind,from=artifactory-scripts,source=configure-artifactory-apt.sh,target=/tmp/configure-artifactory-apt.sh \
    --mount=type=secret,id=artifactory_token \
    /bin/bash /tmp/configure-artifactory-apt.sh "$ARTIFACTORY_URL" "$ARTIFACTORY_USER" "$ARTIFACTORY_CA_CERT"

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
&& echo "StrictModes no" >>/etc/ssh/sshd_config

COPY files/balancer-primary-entrypoint.sh /root/entrypoint.sh
COPY files/primary-slim-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf

# Cleanup Artifactory configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=artifactory-scripts,source=cleanup-artifactory-apt.sh,target=/tmp/cleanup-artifactory-apt.sh \
    /bin/bash /tmp/cleanup-artifactory-apt.sh

CMD ["/root/entrypoint.sh"]
