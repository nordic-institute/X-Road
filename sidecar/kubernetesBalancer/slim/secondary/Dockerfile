FROM niis/xroad-security-server-sidecar:6.24.0-slim
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync cron
COPY --chown=root:root files/slim-secondary-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY files/balancer-slim-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
