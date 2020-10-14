FROM niis/xroad-security-server-sidecar:latest
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync cron
COPY --chown=root:root files/consumer-secondary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-consumer-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
