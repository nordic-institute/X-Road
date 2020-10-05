FROM niis/xroad-security-server-sidecar:latest-provider
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync
COPY files/balancer-provider-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]