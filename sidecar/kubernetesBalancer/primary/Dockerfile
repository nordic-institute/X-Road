ARG VERSION=6.25.0
FROM niis/xroad-security-server-sidecar:$VERSION
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync

RUN adduser --system --shell /bin/bash --ingroup xroad xroad-slave \
&& mkdir -m 755 -p /home/xroad-slave/.ssh && sudo touch /home/xroad-slave/.ssh/authorized_keys \
&& crudini --set /etc/xroad/conf.d/node.ini node type 'master' \
&& chown xroad:xroad /etc/xroad/conf.d/node.ini \
&& cp -a /etc/xroad/conf.d/node.ini /root/etc/xroad/conf.d/ \
&& crudini --set /etc/xroad/conf.d/override-docker.ini proxy server-support-clients-pooled-connections 'false' \
&& cp -a /etc/xroad/conf.d/override-docker.ini /root/etc/xroad/conf.d/

COPY files/balancer-primary-entrypoint.sh /root/entrypoint.sh
COPY files/primary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]
