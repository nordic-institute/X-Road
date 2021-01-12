ARG VERSION=6.25.0
FROM niis/xroad-security-server-sidecar:$VERSION-slim
RUN apt-get -qq update \
    && apt-get -qq install openssh-server rsync
COPY files/balancer-slim-primary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
