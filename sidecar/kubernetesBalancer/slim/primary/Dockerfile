FROM niis/xroad-security-server-sidecar:6.24.0-slim
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync
COPY files/balancer-slim-primary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
