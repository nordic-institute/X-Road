FROM niis/xroad-security-server-sidecar:latest
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync
COPY files/balancer-consumer-primary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]