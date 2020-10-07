FROM niis/xroad-security-server-sidecar:latest-provider
RUN apt-get -qq update
RUN apt-get -qq install cron
RUN apt-get -qq install openssh-server rsync

COPY files/xroad-sync.conf /etc/init/xroad-sync.conf
COPY files/xroad-sync-wait.conf  /etc/init/xroad-sync-wait.conf
COPY files/balancer-provider-secondary-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
