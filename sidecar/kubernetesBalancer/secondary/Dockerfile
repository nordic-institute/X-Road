ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION
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
CMD ["/root/entrypoint.sh"]
