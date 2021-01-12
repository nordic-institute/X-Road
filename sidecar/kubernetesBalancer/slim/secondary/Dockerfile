ARG VERSION=6.25.0
FROM niis/xroad-security-server-sidecar:$VERSION-slim
RUN apt-get -qq update && apt-get -qq install rsync cron
COPY --chown=root:root files/slim-secondary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-slim-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
