FROM niis/xroad-security-server-sidecar:latest-provider
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync cron
COPY --chown=root:root files/provider-secondary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-provider-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
