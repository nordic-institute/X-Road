FROM niis/xroad-security-server-sidecar:latest-provider
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync
COPY files/balancer-master-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/provider-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]